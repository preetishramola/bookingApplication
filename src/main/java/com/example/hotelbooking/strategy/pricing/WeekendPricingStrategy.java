package com.example.hotelbooking.strategy.pricing;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class WeekendPricingStrategy implements PricingStrategy {

    private static final double WEEKEND_SURCHARGE_RATE = 0.15;

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
        double total = 0.0;

        for (long i = 0; i < nights; i++) {
            LocalDate currentDate = checkIn.plusDays(i);
            DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

            double nightlyCharge = basePricePerNight;
            if (isWeekend) {
                nightlyCharge += basePricePerNight * WEEKEND_SURCHARGE_RATE;
            }
            total += nightlyCharge;
        }

        return total;
    }
}
