package com.example.hotelbooking.decorator;

public class BaseRoomPrice implements BookingPriceComponent {

    private final double nightlyRate;
    private final int nights;

    public BaseRoomPrice(double nightlyRate, int nights) {
        if (nightlyRate < 0) {
            throw new IllegalArgumentException("Nightly rate cannot be negative");
        }
        if (nights < 0) {
            throw new IllegalArgumentException("Number of nights cannot be negative");
        }

        this.nightlyRate = nightlyRate;
        this.nights = nights;
    }

    @Override
    public double getCost() {
        return nightlyRate * nights;
    }

    @Override
    public String getDescription() {
        return "Base Room charge";
    }
}
