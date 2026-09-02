package com.example.hotelbooking.decorator;

public class BreakfastAddOn extends AddOnDecorator {

    private static final double BREAKFAST_COST = 30.0;

    public BreakfastAddOn(BookingPriceComponent wrappedComponent) {
        super(wrappedComponent);
    }

    @Override
    protected double getAddOnCost() {
        return BREAKFAST_COST;
    }

    @Override
    protected String getAddOnName() {
        return "Breakfast";
    }
}
