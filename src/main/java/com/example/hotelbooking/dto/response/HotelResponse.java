package com.example.hotelbooking.dto.response;

import com.example.hotelbooking.entity.Hotel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HotelResponse {
    private Long id;
    private String name;
    private String address;
    private String city;
    private String state;
    private String country;
    /** Average of verified guest reviews (1 decimal), or the hotel's initial rating while it has none. */
    private Double rating;
    private Integer reviewCount;
    private String description;
    private Double latitude;
    private Double longitude;
    private List<String> amenities;

    public static HotelResponse from(Hotel hotel) {
        return HotelResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .address(hotel.getAddress())
                .city(hotel.getCity())
                .state(hotel.getState())
                .country(hotel.getCountry())
                .rating(roundToOneDecimal(hotel.getEffectiveRating()))
                .reviewCount(hotel.getReviewCount() == null ? 0 : hotel.getReviewCount())
                .description(hotel.getDescription())
                .latitude(hotel.getLatitude())
                .longitude(hotel.getLongitude())
                .amenities(List.copyOf(hotel.getAmenities()))
                .build();
    }

    private static Double roundToOneDecimal(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
