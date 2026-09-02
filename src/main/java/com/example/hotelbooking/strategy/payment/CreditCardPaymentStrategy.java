package com.example.hotelbooking.strategy.payment;

import java.util.Locale;

public class CreditCardPaymentStrategy implements PaymentStrategy {

    @Override
    public String pay(double amount) {
        return String.format(Locale.US, "Processed credit card payment of $%.2f", amount);
    }
}
