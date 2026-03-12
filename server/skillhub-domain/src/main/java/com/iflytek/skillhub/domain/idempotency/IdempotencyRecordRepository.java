package com.iflytek.skillhub.domain.idempotency;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository {
    Optional<IdempotencyRecord> findByRequestId(String requestId);
    IdempotencyRecord save(IdempotencyRecord record);
    int deleteExpired(Instant now);
    int markStaleAsFailed(Instant threshold);
}
