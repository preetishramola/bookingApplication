package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.response.HotelResponse;
import com.example.hotelbooking.dto.response.HotelSearchResult;
import com.example.hotelbooking.dto.response.RoomResponse;
import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.service.HotelService;
import com.example.hotelbooking.service.BookingService;
import com.example.hotelbooking.service.HotelSearchService;
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
    private final HotelSearchService hotelSearchService;

    // GET /api/hotels/search?name=&city=&state=&country=&minRating=  (all optional)
    @GetMapping("/search")
    public ResponseEntity<List<HotelResponse>> searchHotels(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Double minRating) {

        List<Hotel> hotels = hotelService.searchHotels(name, city, state, country, minRating);
        return ResponseEntity.ok(hotels.stream().map(HotelResponse::from).toList());
    }

    // GET /api/hotels/nearby?lat=12.97&lng=77.59&radiusKm=5&limit=20
    // PostGIS: hotels within radiusKm (default 5) of the point, nearest first
    @GetMapping("/nearby")
    public ResponseEntity<List<HotelSearchResult>> findNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(hotelSearchService.findNearby(lat, lng, radiusKm, limit));
    }

    // GET /api/hotels/vibe-search?q=quiet romantic boutique hotel near Cubbon Park&limit=10
    // Optional &lat=&lng=&radiusKm= to only rank hotels near a point.
    @GetMapping("/vibe-search")
    public ResponseEntity<List<HotelSearchResult>> vibeSearch(
            @RequestParam String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(hotelSearchService.vibeSearch(q, lat, lng, radiusKm, limit));
    }

    // GET /api/hotels/{hotelId}
    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelResponse> getHotel(@PathVariable Long hotelId) {
        return ResponseEntity.ok(HotelResponse.from(hotelService.getHotel(hotelId)));
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