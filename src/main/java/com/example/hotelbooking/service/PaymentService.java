package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Payment;
import com.example.hotelbooking.enums.PaymentStatus;
import com.example.hotelbooking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public Payment processPayment(Booking booking) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        
        // Simulating a successful external payment gateway call
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentTime(LocalDateTime.now());
        
        return paymentRepository.save(payment);
    }
}