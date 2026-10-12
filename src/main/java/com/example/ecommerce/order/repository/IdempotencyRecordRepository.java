package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
    Optional<IdempotencyRecord> findByIdempotencyKeyAndCreatedAtAfter(String idempotencyKey, LocalDateTime after);
}
