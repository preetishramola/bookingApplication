package com.example.hotelbooking.aspect;

import com.example.hotelbooking.entity.IdempotencyRecord;
import com.example.hotelbooking.repository.IdempotencyRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyRepository idempotencyRepository;

    @Around("@annotation(com.example.hotelbooking.annotation.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        // The frontend MUST send this header
        String idempotencyKey = request.getHeader("Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
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

        // If the save was successful, proceed to the actual BookingController logic
        return joinPoint.proceed();
    }
}