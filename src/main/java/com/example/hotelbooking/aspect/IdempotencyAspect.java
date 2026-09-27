package com.example.hotelbooking.aspect;

import com.example.hotelbooking.entity.IdempotencyRecord;
import com.example.hotelbooking.repository.IdempotencyRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

// HIGHEST_PRECEDENCE makes this run OUTSIDE the method's @Transactional.
// That way the key is committed on its own (so a concurrent duplicate is blocked right away),
// and a failed insert can't mark the booking transaction as rollback-only.
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyRepository idempotencyRepository;

    @Around("@annotation(com.example.hotelbooking.annotation.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        // The frontend MUST send this header
        String idempotencyKey = request.getHeader("Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Idempotency-Key header is missing");
        }

        try {
            // We attempt to INSERT the key BEFORE running the booking logic.
            // If another thread is doing this at the exact same millisecond,
            // the database's Primary Key constraint will block the second one and throw an error!
            idempotencyRepository.saveAndFlush(new IdempotencyRecord(idempotencyKey, LocalDateTime.now()));

        } catch (DataIntegrityViolationException e) {
            // The duplicate request is caught here!
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Duplicate request detected. This action was already processed.");
        }

        try {
            // If the save was successful, proceed to the actual BookingController logic
            return joinPoint.proceed();
        } catch (Throwable ex) {
            // The action failed (e.g. room unavailable, payment failed), so nothing was processed.
            // Release the key so the client can retry with the same Idempotency-Key.
            idempotencyRepository.deleteById(idempotencyKey);
            throw ex;
        }
    }
}
