package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.response.PagedResponse;
import com.example.hotelbooking.dto.response.ReviewResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.entity.Review;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.repository.HotelRepository;
import com.example.hotelbooking.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Verified guest reviews.
 *
 * Rules: only the guest who made a booking can review it; the booking must be CONFIRMED and its check-in
 * date must have arrived; one review per booking. Anyone can read reviews. Authors can edit or delete their
 * own review, admins can delete any review. Cancelling a booking removes its review.
 *
 * Every write locks the hotel row first, then recomputes the hotel's review count and average from the
 * reviews table. The lock serialises concurrent reviews of the same hotel, so the stored average can't
 * drift and the one-review-per-booking check can't race (the unique constraint is the backstop).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;

    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getHotelReviews(Long hotelId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel not found");
        }
        return PagedResponse.from(
                reviewRepository.findByHotelIdOrderByCreatedAtDescIdDesc(hotelId, PageRequest.of(page, size)),
                review -> toResponse(review, false));
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(String userEmail) {
        return reviewRepository.findByUserEmailOrderByCreatedAtDesc(userEmail).stream()
                .map(review -> toResponse(review, true))
                .toList();
    }

    @Transactional
    public ReviewResponse createReview(Long bookingId, String userEmail, int rating, String comment) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("You can only review your own stays");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed stays can be reviewed");
        }
        if (booking.getCheckIn().isAfter(LocalDate.now())) {
            throw new IllegalStateException("You can review this stay from your check-in date, " + booking.getCheckIn());
        }

        Hotel hotel = lockHotel(booking.getRoom().getHotel().getId());

        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new IllegalStateException("You have already reviewed this stay");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setHotel(hotel);
        review.setUser(booking.getUser());
        review.setRating(rating);
        review.setComment(clean(comment));
        reviewRepository.saveAndFlush(review);

        refreshHotelRating(hotel);
        return toResponse(review, true);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, String userEmail, int rating, String comment) {
        Review review = reviewRepository.findWithDetailsById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getUser().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("You can only edit your own review");
        }

        Hotel hotel = lockHotel(review.getHotel().getId());
        review.setRating(rating);
        review.setComment(clean(comment));
        review.setUpdatedAt(LocalDateTime.now());
        reviewRepository.saveAndFlush(review);

        refreshHotelRating(hotel);
        return toResponse(review, true);
    }

    @Transactional
    public void deleteReview(Long reviewId, String requesterEmail, boolean requesterIsAdmin) {
        Review review = reviewRepository.findWithDetailsById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!requesterIsAdmin && !review.getUser().getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("You can only delete your own review");
        }
        removeAndRefresh(review);
    }

    /** Called when a booking is cancelled: a cancelled stay can't keep a verified review. */
    @Transactional
    public void removeReviewForCancelledBooking(Long bookingId) {
        reviewRepository.findByBookingId(bookingId).ifPresent(this::removeAndRefresh);
    }

    private void removeAndRefresh(Review review) {
        Hotel hotel = lockHotel(review.getHotel().getId());
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshHotelRating(hotel);
    }

    private Hotel lockHotel(Long hotelId) {
        return hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));
    }

    // Runs after the lock is held, so this statement sees every review committed before us.
    private void refreshHotelRating(Hotel hotel) {
        ReviewRepository.RatingStats stats = reviewRepository.statsForHotel(hotel.getId());
        int count = (int) stats.getCount();
        hotel.setReviewCount(count);
        hotel.setAverageReviewRating(count == 0 ? null : stats.getAverage());
        hotelRepository.save(hotel);
    }

    private static String clean(String comment) {
        return StringUtils.hasText(comment) ? comment.trim() : null;
    }

    private static ReviewResponse toResponse(Review review, boolean forAuthor) {
        LocalDate checkIn = review.getBooking().getCheckIn();
        return ReviewResponse.builder()
                .id(review.getId())
                .hotelId(review.getHotel().getId())
                .hotelName(review.getHotel().getName())
                .bookingId(forAuthor ? review.getBooking().getId() : null)
                .reviewerName(displayName(review.getUser().getName()))
                .rating(review.getRating())
                .comment(review.getComment())
                .stayMonth(String.format("%d-%02d", checkIn.getYear(), checkIn.getMonthValue()))
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    /** "Alice Johnson" -> "Alice J."; "Alice" -> "Alice". Keeps full names and emails private. */
    static String displayName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return "Guest";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }
}
