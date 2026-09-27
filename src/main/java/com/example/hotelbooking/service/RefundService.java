package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.Booking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * DEMO / PLACEHOLDER: refunds are not implemented yet.
 *
 * BookingService already calls this when a booking is cancelled, so the real
 * implementation only needs to fill in processRefund(). Planned work:
 *  - look up the Payment for the booking
 *  - decide the refund amount (cancellation policy, e.g. full refund N days before check-in)
 *  - refund through the original PaymentStrategy (UPI / credit card)
 *  - mark the Payment as REFUNDED (add that PaymentStatus)
 */
@Slf4j
@Service
public class RefundService {

    public void processRefund(Booking booking) {
        log.info("[DEMO] Refund not implemented yet. Booking {} for amount {} would be refunded here.",
                booking.getId(), booking.getTotalAmount());
    }
}
