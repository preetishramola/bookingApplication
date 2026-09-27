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
    private Double rating;
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
                .rating(hotel.getRating())
                .description(hotel.getDescription())
                .latitude(hotel.getLatitude())
                .longitude(hotel.getLongitude())
                .amenities(List.copyOf(hotel.getAmenities()))
                .build();
    }
}
