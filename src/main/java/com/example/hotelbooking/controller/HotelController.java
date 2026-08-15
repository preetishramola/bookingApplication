package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.response.RoomResponse;
import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.service.HotelService;
import com.example.hotelbooking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final BookingService bookingService;

    // GET /api/hotels/{hotelId}
    @GetMapping("/{hotelId}")
    public ResponseEntity<Hotel> getHotel(@PathVariable Long hotelId) {
        // For V1, returning the entity directly is okay for a simple GET, 
        // but we normally map this to a HotelResponse DTO too.
        return ResponseEntity.ok(hotelService.getHotel(hotelId));
    }

    // GET /api/hotels/{hotelId}/rooms
    @GetMapping("/{hotelId}/rooms")
    public ResponseEntity<List<RoomResponse>> getRooms(@PathVariable Long hotelId) {
        List<Room> rooms = hotelService.getRooms(hotelId);
        return ResponseEntity.ok(mapToRoomResponseList(rooms));
    }

    // GET /api/hotels/{hotelId}/rooms/available?checkIn=2024-08-20&checkOut=2024-08-23
    @GetMapping("/{hotelId}/rooms/available")
    public ResponseEntity<List<RoomResponse>> getAvailableRooms(
            @PathVariable Long hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        
        List<Room> availableRooms = bookingService.getAvailableRooms(hotelId, checkIn, checkOut);
        return ResponseEntity.ok(mapToRoomResponseList(availableRooms));
    }

    // Helper method to map Entities to DTOs
    private List<RoomResponse> mapToRoomResponseList(List<Room> rooms) {
        return rooms.stream()
                .map(room -> RoomResponse.builder()
                        .id(room.getId())
                        .roomNumber(room.getRoomNumber())
                        .type(room.getType().name())
                        .pricePerNight(room.getPricePerNight())
                        .build())
                .collect(Collectors.toList());
    }
}