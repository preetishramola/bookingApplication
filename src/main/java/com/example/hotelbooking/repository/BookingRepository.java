package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);
    
    List<Booking> findByRoomId(Long roomId);

    /**
     * Checks if there are any active bookings for a given room that overlap 
     * with the requested check-in and check-out dates.
     * 
     * We ignore 'CANCELLED' bookings because cancelled bookings don't block the room.
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Booking b " +
           "WHERE b.room.id = :roomId " +
           "AND b.status != 'CANCELLED' " +
           "AND b.checkIn < :checkOut " +
           "AND b.checkOut > :checkIn")
    boolean existsOverlappingBooking(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}