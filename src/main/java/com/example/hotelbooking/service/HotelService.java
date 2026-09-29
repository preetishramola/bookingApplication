package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.request.HotelRequest;
import com.example.hotelbooking.dto.request.RoomRequest;
import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.enums.RoomStatus;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.HotelRepository;
import com.example.hotelbooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelEmbeddingService embeddingService;

    public Hotel getHotel(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));
    }

    public List<Room> getRooms(Long hotelId) {
        // First verify the hotel exists
        getHotel(hotelId);
        return roomRepository.findByHotelId(hotelId);
    }

    /**
     * Every filter is optional. City/state/country match exactly (case-insensitive),
     * name is a partial match, minRating is inclusive.
     */
    public List<Hotel> searchHotels(String name, String city, String state, String country, Double minRating) {
        Specification<Hotel> spec = Specification.where(null);

        if (StringUtils.hasText(name)) {
            String pattern = "%" + name.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        spec = spec.and(equalsIgnoreCase("city", city))
                .and(equalsIgnoreCase("state", state))
                .and(equalsIgnoreCase("country", country));
        if (minRating != null) {
            // Same rule as Hotel.getEffectiveRating(): review average when there are reviews, else the initial rating
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(
                    cb.coalesce(root.<Double>get("averageReviewRating"), root.<Double>get("rating")), minRating));
        }

        return hotelRepository.findAll(spec).stream()
                .sorted(Comparator.comparing(Hotel::getEffectiveRating,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Saves the hotel, then generates its vibe-search embedding. Not one transaction on purpose:
     * if the embedding model is down the hotel is still created, just without an embedding
     * (fix later with POST /api/admin/hotels/embeddings/refresh).
     */
    public Hotel addHotel(HotelRequest request) {
        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new IllegalArgumentException("Provide both latitude and longitude, or neither");
        }

        Hotel hotel = new Hotel();
        hotel.setName(request.getName().trim());
        hotel.setAddress(request.getAddress());
        hotel.setCity(request.getCity().trim());
        hotel.setState(request.getState());
        hotel.setCountry(request.getCountry());
        hotel.setRating(request.getRating());
        hotel.setDescription(request.getDescription());
        hotel.setLatitude(request.getLatitude());
        hotel.setLongitude(request.getLongitude());
        if (request.getAmenities() != null) {
            request.getAmenities().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .forEach(hotel.getAmenities()::add);
        }

        Hotel saved = hotelRepository.save(hotel);
        embeddingService.embedHotel(saved);
        return saved;
    }

    @Transactional
    public Room addRoom(Long hotelId, RoomRequest request) {
        Hotel hotel = getHotel(hotelId);

        boolean duplicate = roomRepository.findByHotelId(hotelId).stream()
                .anyMatch(room -> room.getRoomNumber().equalsIgnoreCase(request.getRoomNumber().trim()));
        if (duplicate) {
            throw new IllegalArgumentException("Room " + request.getRoomNumber() + " already exists in this hotel");
        }

        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomNumber(request.getRoomNumber().trim());
        room.setType(request.getType());
        room.setPricePerNight(request.getPricePerNight());
        room.setStatus(RoomStatus.AVAILABLE);
        return roomRepository.save(room);
    }

    private static Specification<Hotel> equalsIgnoreCase(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get(field)), normalized);
    }
}
