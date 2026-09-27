package com.example.hotelbooking.controller;

import com.example.hotelbooking.annotation.Idempotent;
import com.example.hotelbooking.dto.request.BookingRequest;
import com.example.hotelbooking.dto.response.BookingResponse;
import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.security.SecurityUtils;
import com.example.hotelbooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// All endpoints require a logged-in user (see SecurityConfig).
// Users can only see/cancel their own bookings; admins can access any booking.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // POST /api/bookings
    @Idempotent
    @Transactional
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request,
                                                         Authentication authentication) {
        Booking booking = bookingService.createBooking(
                authentication.getName(),
                request.getRoomId(),
                request.getCheckIn(),
                request.getCheckOut(),
                request.getAddOns(),
                request.getPaymentMethod()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToBookingResponse(booking));
    }

    // GET /api/bookings/{bookingId}
    @Transactional(readOnly = true)
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long bookingId, Authentication authentication) {
        Booking booking = bookingService.getBooking(
                bookingId, authentication.getName(), SecurityUtils.isAdmin(authentication));
        return ResponseEntity.ok(mapToBookingResponse(booking));
    }

    // DELETE /api/bookings/{bookingId}
    @Transactional
    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId, Authentication authentication) {
        bookingService.cancelBooking(bookingId, authentication.getName(), SecurityUtils.isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    // GET /api/bookings/me
    @Transactional(readOnly = true)
    @GetMapping("/bookings/me")
    public ResponseEntity<List<BookingResponse>> getMyBookings(Authentication authentication) {
        List<Booking> bookings = bookingService.getMyBookings(authentication.getName());
        return ResponseEntity.ok(bookings.stream().map(this::mapToBookingResponse).toList());
    }

    // GET /api/users/{userId}/bookings
    @Transactional(readOnly = true)
    @GetMapping("/users/{userId}/bookings")
    public ResponseEntity<List<BookingResponse>> getUserBookings(@PathVariable Long userId,
                                                                 Authentication authentication) {
        List<Booking> bookings = bookingService.getUserBookings(
                userId, authentication.getName(), SecurityUtils.isAdmin(authentication));
        return ResponseEntity.ok(bookings.stream().map(this::mapToBookingResponse).toList());
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
