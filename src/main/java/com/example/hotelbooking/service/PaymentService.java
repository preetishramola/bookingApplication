package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.enums.PaymentMethod;
import com.example.hotelbooking.enums.PaymentStatus;
import com.example.hotelbooking.repository.PaymentRepository;
import com.example.hotelbooking.strategy.payment.CreditCardPaymentStrategy;
import com.example.hotelbooking.strategy.payment.PaymentStrategy;
import com.example.hotelbooking.strategy.payment.UpiPaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public Payment processPayment(Booking booking) {
        return processPayment(booking, "CREDIT_CARD");
    }

    public Payment processPayment(Booking booking, String paymentMethod) {
        String normalizedMethod = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);

        PaymentMethod method = switch (normalizedMethod) {
            case "UPI" -> PaymentMethod.UPI;
            case "CREDIT_CARD" -> PaymentMethod.CREDIT_CARD;
            default -> throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        };

        PaymentStrategy paymentStrategy = switch (normalizedMethod) {
            case "UPI" -> new UpiPaymentStrategy();
            case "CREDIT_CARD" -> new CreditCardPaymentStrategy();
            default -> throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        };

        paymentStrategy.pay(booking.getTotalAmount());

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentTime(LocalDateTime.now());

        return paymentRepository.save(payment);
    }
}