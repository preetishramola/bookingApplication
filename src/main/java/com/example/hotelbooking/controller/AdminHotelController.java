package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.request.HotelRequest;
import com.example.hotelbooking.dto.request.RoomRequest;
import com.example.hotelbooking.dto.response.HotelResponse;
import com.example.hotelbooking.dto.response.RoomResponse;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.service.HotelEmbeddingService;
import com.example.hotelbooking.service.HotelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/hotels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHotelController {

    private final HotelService hotelService;
    private final HotelEmbeddingService embeddingService;

    // POST /api/admin/hotels/add
    @PostMapping("/add")
    public ResponseEntity<HotelResponse> addHotel(@Valid @RequestBody HotelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(HotelResponse.from(hotelService.addHotel(request)));
    }

    // POST /api/admin/hotels/embeddings/refresh?onlyMissing=true
    // Generates vibe-search embeddings for hotels that don't have one (or all hotels with onlyMissing=false)
    @PostMapping("/embeddings/refresh")
    public ResponseEntity<HotelEmbeddingService.RefreshResult> refreshEmbeddings(
            @RequestParam(defaultValue = "true") boolean onlyMissing) {
        HotelEmbeddingService.RefreshResult result = embeddingService.refreshEmbeddings(onlyMissing);
        HttpStatus status = result.failed() > 0 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    // POST /api/admin/hotels/{hotelId}/rooms
    @PostMapping("/{hotelId}/rooms")
    public ResponseEntity<RoomResponse> addRoom(@PathVariable Long hotelId, @Valid @RequestBody RoomRequest request) {
        Room room = hotelService.addRoom(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.builder()
                .id(room.getId())
                .roomNumber(room.getRoomNumber())
                .type(room.getType().name())
                .pricePerNight(room.getPricePerNight())
                .build());
    }
}
