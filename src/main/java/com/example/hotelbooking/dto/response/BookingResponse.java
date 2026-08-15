package com.example.hotelbooking.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class BookingResponse {
    private Long bookingId;
    private Long roomId;
    private String hotelName;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Double totalAmount;
    private String status;
    private LocalDateTime createdAt;
}