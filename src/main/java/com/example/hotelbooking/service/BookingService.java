package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.enums.RoomStatus;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.exception.RoomUnavailableException;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.repository.RoomRepository;
import com.example.hotelbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

    /**
     * Retrieves all rooms for a hotel that are physically AVAILABLE 
     * AND have no overlapping bookings for the given dates.
     */
    public List<Room> getAvailableRooms(Long hotelId, LocalDate checkIn, LocalDate checkOut) {
        if (checkIn.isAfter(checkOut) || checkIn.isEqual(checkOut)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        List<Room> allRoomsInHotel = roomRepository.findByHotelId(hotelId);

        // Filter out rooms that are under maintenance OR have overlapping bookings
        return allRoomsInHotel.stream()
                .filter(room -> room.getStatus() == RoomStatus.AVAILABLE)
                .filter(room -> !bookingRepository.existsOverlappingBooking(room.getId(), checkIn, checkOut))
                .collect(Collectors.toList());
    }

    /**
     * @Transactional ensures that if the payment fails (or any error occurs), 
     * the booking is rolled back and not saved to the database.
     */
    @Transactional
    public Booking createBooking(Long userId, Long roomId, LocalDate checkIn, LocalDate checkOut) {
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        // RULE 4 & 5: Check availability right before booking
        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new RoomUnavailableException("Room is currently under maintenance.");
        }

        if (bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut)) {
            throw new RoomUnavailableException("Room is already booked for the selected dates.");
        }

        // Calculate total amount
        long numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
        double totalAmount = numberOfNights * room.getPricePerNight();

        // Create Booking
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoom(room);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkOut);
        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.CONFIRMED); // For V1, we confirm immediately upon mock payment

        // Save booking first so the Payment has a valid booking_id to reference
        Booking savedBooking = bookingRepository.save(booking);

        // Process Mock Payment
        paymentService.processPayment(savedBooking);

        return savedBooking;
    }

    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = getBooking(bookingId);
        
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }
        
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        
        // In the future: Add refund logic via PaymentService here
    }
}