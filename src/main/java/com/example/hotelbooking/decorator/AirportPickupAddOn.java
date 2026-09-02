package com.example.hotelbooking.decorator;

public class AirportPickupAddOn extends AddOnDecorator {

    private static final double AIRPORT_PICKUP_COST = 80.0;

    public AirportPickupAddOn(BookingPriceComponent wrappedComponent) {
        super(wrappedComponent);
    }

    @Override
    protected double getAddOnCost() {
        return AIRPORT_PICKUP_COST;
    }

    @Override
    protected String getAddOnName() {
        return "Airport Pickup";
    }
}
