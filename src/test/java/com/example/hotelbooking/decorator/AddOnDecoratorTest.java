package com.example.hotelbooking.decorator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddOnDecoratorTest {

    @Test
    void addOnsStackOnTopOfBasePrice() {
        BookingPriceComponent price = new AirportPickupAddOn(new BreakfastAddOn(new BaseRoomPrice(100, 2)));

        assertThat(price.getCost()).isEqualTo(200 + 30 + 80);
        assertThat(price.getDescription()).isEqualTo("Base Room charge + Breakfast + Airport Pickup");
    }
}
