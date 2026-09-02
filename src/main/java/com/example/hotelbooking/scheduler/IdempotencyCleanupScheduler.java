package com.example.hotelbooking.scheduler;

import com.example.hotelbooking.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final IdempotencyRepository idempotencyRepository;

    /**
     * This cron expression means: Run at 02:00 AM every single day.
     * (Seconds: 0, Minutes: 0, Hours: 2, Day of Month: *, Month: *, Day of Week: *)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanUpOldIdempotencyKeys() {

        // 1. Calculate the dynamic threshold (24 hours ago from right now)
        LocalDateTime thresholdDate = LocalDateTime.now().minusHours(24);

        // 2. Delete everything older than that exact timestamp
        idempotencyRepository.deleteByCreatedAtBefore(thresholdDate);

        // Optional: Print a log so you know it worked
        System.out.println("Successfully cleaned up idempotency keys older than: " + thresholdDate);
    }
}