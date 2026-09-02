package com.example.hotelbooking.decorator;

public abstract class AddOnDecorator implements BookingPriceComponent {

    protected final BookingPriceComponent wrappedComponent;

    protected AddOnDecorator(BookingPriceComponent wrappedComponent) {
        this.wrappedComponent = wrappedComponent;
    }

    @Override
    public double getCost() {
        return wrappedComponent.getCost() + getAddOnCost();
    }

    @Override
    public String getDescription() {
        return wrappedComponent.getDescription() + " + " + getAddOnName();
    }

    protected abstract double getAddOnCost();

    protected abstract String getAddOnName();
}
