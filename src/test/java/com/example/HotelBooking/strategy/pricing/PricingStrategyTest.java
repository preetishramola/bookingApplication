package com.example.hotelbooking.strategy.pricing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingStrategyTest {

    // 2026-10-05 is a Monday
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Test
    void standardPricingChargesBasePricePerNight() {
        double price = new StandardPricingStrategy().calculatePrice(100, MONDAY, MONDAY.plusDays(3));
        assertThat(price).isEqualTo(300.0);
    }

    @Test
    void weekendPricingAddsSurchargeOnlyForSaturdayAndSundayNights() {
        // Fri, Sat, Sun nights -> 100 + 115 + 115
        LocalDate friday = MONDAY.plusDays(4);
        double price = new WeekendPricingStrategy().calculatePrice(100, friday, friday.plusDays(3));
        assertThat(price).isEqualTo(330.0);
    }

    @Test
    void rejectsCheckOutNotAfterCheckIn() {
        assertThatThrownBy(() -> new StandardPricingStrategy().calculatePrice(100, MONDAY, MONDAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeekendPricingStrategy().calculatePrice(100, MONDAY, MONDAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
