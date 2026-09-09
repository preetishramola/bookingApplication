package com.example.hotelbooking.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HotelRequest {
    private String name;
    private String location;
    private String description;
}
