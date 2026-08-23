package com.example.hotelbooking.strategy.pricing;

import java.time.LocalDate;

public interface PricingStrategy {
    double calculatePrice(double basePricePerNight, LocalDate checkIn, LocalDate checkOut);
}
