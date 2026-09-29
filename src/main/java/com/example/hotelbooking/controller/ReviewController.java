package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.request.ReviewRequest;
import com.example.hotelbooking.dto.response.PagedResponse;
import com.example.hotelbooking.dto.response.ReviewResponse;
import com.example.hotelbooking.security.SecurityUtils;
import com.example.hotelbooking.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Reading reviews is public (GET /api/hotels/** is permitAll in SecurityConfig).
// Writing requires a login; the service checks that the caller really stayed (owns the booking).
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // GET /api/hotels/{hotelId}/reviews?page=0&size=10   newest first
    @GetMapping("/hotels/{hotelId}/reviews")
    public ResponseEntity<PagedResponse<ReviewResponse>> getHotelReviews(
            @PathVariable Long hotelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.getHotelReviews(hotelId, page, size));
    }

    // POST /api/bookings/{bookingId}/review   {"rating":5,"comment":"..."}
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/bookings/{bookingId}/review")
    public ResponseEntity<ReviewResponse> createReview(@PathVariable Long bookingId,
                                                       @Valid @RequestBody ReviewRequest request,
                                                       Authentication authentication) {
        ReviewResponse review = reviewService.createReview(
                bookingId, authentication.getName(), request.getRating(), request.getComment());
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    // PUT /api/reviews/{reviewId}   author only
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(@PathVariable Long reviewId,
                                                       @Valid @RequestBody ReviewRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(reviewService.updateReview(
                reviewId, authentication.getName(), request.getRating(), request.getComment()));
    }

    // DELETE /api/reviews/{reviewId}   author or admin
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId, Authentication authentication) {
        reviewService.deleteReview(reviewId, authentication.getName(), SecurityUtils.isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    // GET /api/reviews/me   the caller's reviews, with bookingId so the UI can match them to bookings
    @GetMapping("/reviews/me")
    public ResponseEntity<List<ReviewResponse>> getMyReviews(Authentication authentication) {
        return ResponseEntity.ok(reviewService.getMyReviews(authentication.getName()));
    }
}
