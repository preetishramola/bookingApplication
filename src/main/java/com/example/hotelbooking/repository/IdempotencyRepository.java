package com.example.hotelbooking.repository;

import com.example.hotelbooking.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Architectural Bonus:
     * This method allows us to delete old idempotency keys so the database
     * table doesn't grow infinitely over the years.
     */
    void deleteByCreatedAtBefore(LocalDateTime thresholdDate);
}