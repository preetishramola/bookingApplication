package com.example.hotelbooking.strategy.pricing;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class StandardPricingStrategy implements PricingStrategy {

    @Override
    public double calculatePrice(double basePricePerNight, LocalDate checkIn, LocalDate checkOut) {
        if (basePricePerNight < 0) {
            throw new IllegalArgumentException("Base price cannot be negative");
        }
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Check-in and check-out dates are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return basePricePerNight * nights;
    }
}
