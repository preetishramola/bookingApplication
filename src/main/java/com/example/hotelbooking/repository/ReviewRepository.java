package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Review count and average rating for one hotel. */
    interface RatingStats {
        long getCount();
        Double getAverage();
    }

    @EntityGraph(attributePaths = {"user", "hotel", "booking"})
    Page<Review> findByHotelIdOrderByCreatedAtDescIdDesc(Long hotelId, Pageable pageable);

    @EntityGraph(attributePaths = {"hotel", "booking"})
    List<Review> findByUserEmailOrderByCreatedAtDesc(String email);

    @EntityGraph(attributePaths = {"user", "hotel", "booking"})
    Optional<Review> findWithDetailsById(Long id);

    Optional<Review> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    @Query("SELECT COUNT(r) AS count, AVG(r.rating) AS average FROM Review r WHERE r.hotel.id = :hotelId")
    RatingStats statsForHotel(@Param("hotelId") Long hotelId);
}
