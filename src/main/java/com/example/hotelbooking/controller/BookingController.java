package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.request.BookingRequest;
import com.example.hotelbooking.dto.response.BookingResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.repository.BookingRepository; // We inject this just for findByUserId for V1
import com.example.hotelbooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository; // Quick injection for user bookings

    // POST /api/bookings
    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        Booking booking = bookingService.createBooking(
                request.getUserId(),
                request.getRoomId(),
                request.getCheckIn(),
                request.getCheckOut()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToBookingResponse(booking));
    }

    // GET /api/bookings/{bookingId}
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long bookingId) {
        Booking booking = bookingService.getBooking(bookingId);
        return ResponseEntity.ok(mapToBookingResponse(booking));
    }

    // DELETE /api/bookings/{bookingId}
    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId) {
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

    // GET /api/users/{userId}/bookings
    @GetMapping("/users/{userId}/bookings")
    public ResponseEntity<List<BookingResponse>> getUserBookings(@PathVariable Long userId) {
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        List<BookingResponse> response = bookings.stream()
                .map(this::mapToBookingResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // Helper method to map Entity to DTO
    private BookingResponse mapToBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .roomId(booking.getRoom().getId())
                .hotelName(booking.getRoom().getHotel().getName()) // Safe due to lazy loading within the transaction
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus().name())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}