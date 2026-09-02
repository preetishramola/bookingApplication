package com.example.hotelbooking.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder // Builder pattern makes it easy to construct these objects
public class RoomResponse {
    private Long id;
    private String roomNumber;
    private String type;
    private Double pricePerNight;
}