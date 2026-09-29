package com.example.hotelbooking.service;

import com.example.hotelbooking.decorator.AirportPickupAddOn;
import com.example.hotelbooking.decorator.BreakfastAddOn;
import com.example.hotelbooking.decorator.BookingPriceComponent;
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
import com.example.hotelbooking.strategy.pricing.PricingStrategy;
import com.example.hotelbooking.strategy.pricing.StandardPricingStrategy;
import com.example.hotelbooking.strategy.pricing.WeekendPricingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final ReviewService reviewService;

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
    public Booking createBooking(String userEmail, Long roomId, LocalDate checkIn, LocalDate checkOut,
                                List<String> addOns, String paymentMethod) {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        User user = getUserByEmail(userEmail);

        // By fetching the room this way, Postgres locks the row.
        // If Thread 2 arrives, it is forced to WAIT here until Thread 1 finishes the transaction!
        Room room = roomRepository.findByIdWithPessimisticLock(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        // Now Thread 1 safely checks availability and saves the booking.

        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new RoomUnavailableException("Room is currently under maintenance.");
        }

        if (bookingRepository.existsOverlappingBooking(roomId, checkIn, checkOut)) {
            throw new RoomUnavailableException("Room is already booked for the selected dates.");
        }

        double totalAmount = calculateTotalAmount(room, checkIn, checkOut, addOns);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoom(room);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkOut);
        booking.setTotalAmount(totalAmount);
        booking.setAddOns(addOns == null ? List.of() : addOns);
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking savedBooking = bookingRepository.save(booking);
        paymentService.processPayment(savedBooking, paymentMethod);

        return savedBooking;
    }

    private double calculateTotalAmount(Room room, LocalDate checkIn, LocalDate checkOut, List<String> addOns) {
        double roomBasePrice = room.getPricePerNight();

        PricingStrategy pricingStrategy = hasWeekendStay(checkIn, checkOut)
                ? new WeekendPricingStrategy()
                : new StandardPricingStrategy();

        double baseTotal = pricingStrategy.calculatePrice(roomBasePrice, checkIn, checkOut);
        BookingPriceComponent totalPrice = new BookingPriceComponent() {
            @Override
            public double getCost() {
                return baseTotal;
            }

            @Override
            public String getDescription() {
                return "Room stay";
            }
        };

        if (addOns != null) {
            for (String addOn : addOns) {
                String normalizedAddOn = addOn == null ? "" : addOn.trim().toUpperCase(Locale.ROOT);
                totalPrice = switch (normalizedAddOn) {
                    case "BREAKFAST" -> new BreakfastAddOn(totalPrice);
                    case "AIRPORT_PICKUP" -> new AirportPickupAddOn(totalPrice);
                    default -> totalPrice;
                };
            }
        }

        return totalPrice.getCost();
    }

    private boolean hasWeekendStay(LocalDate checkIn, LocalDate checkOut) {
        LocalDate current = checkIn;
        while (!current.isEqual(checkOut)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                return true;
            }
            current = current.plusDays(1);
        }
        return false;
    }

    /**
     * Returns the booking only if the requester owns it or is an admin.
     */
    public Booking getBooking(Long bookingId, String requesterEmail, boolean requesterIsAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!requesterIsAdmin && !booking.getUser().getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("You are not allowed to access this booking");
        }
        return booking;
    }

    public List<Booking> getUserBookings(Long userId, String requesterEmail, boolean requesterIsAdmin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!requesterIsAdmin && !user.getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("You are not allowed to view this user's bookings");
        }
        return bookingRepository.findByUserId(userId);
    }

    public List<Booking> getMyBookings(String userEmail) {
        return bookingRepository.findByUserId(getUserByEmail(userEmail).getId());
    }

    @Transactional
    public void cancelBooking(Long bookingId, String requesterEmail, boolean requesterIsAdmin) {
        Booking booking = getBooking(bookingId, requesterEmail, requesterIsAdmin);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        reviewService.removeReviewForCancelledBooking(booking.getId());

        refundService.processRefund(booking);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}