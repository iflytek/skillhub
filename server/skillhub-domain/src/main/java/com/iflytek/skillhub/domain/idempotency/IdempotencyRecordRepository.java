package com.iflytek.skillhub.domain.idempotency;

import java.time.Instant;
import java.util.Optional;

/**
 * Domain repository contract for tracking request idempotency state and cleanup operations.
 */
public interface IdempotencyRecordRepository {
    Optional<IdempotencyRecord> findByRequestId(String requestId);
    IdempotencyRecord save(IdempotencyRecord record);
    int deleteExpired(Instant now);
    int markStaleAsFailed(Instant threshold);
}
