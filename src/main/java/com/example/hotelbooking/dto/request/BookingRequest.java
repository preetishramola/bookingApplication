package com.example.hotelbooking.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class BookingRequest {
    @NotNull(message = "User ID cannot be null")
    private Long userId;

    @NotNull(message = "Room ID cannot be null")
    private Long roomId;

    @NotNull(message = "Check-in date cannot be null")
    @FutureOrPresent(message = "Check-in date cannot be in the past")
    private LocalDate checkIn;

    @NotNull(message = "Check-out date cannot be null")
    @Future(message = "Check-out date must be in the future")
    private LocalDate checkOut;

    private List<String> addOns = new ArrayList<>();

    @NotNull(message = "Payment method cannot be null")
    private String paymentMethod;
}