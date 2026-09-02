package com.example.hotelbooking.strategy.payment;

import java.util.Locale;

public class UpiPaymentStrategy implements PaymentStrategy {

    @Override
    public String pay(double amount) {
        return String.format(Locale.US, "Processed UPI payment of $%.2f", amount);
    }
}
