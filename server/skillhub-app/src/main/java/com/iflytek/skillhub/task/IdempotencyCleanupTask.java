package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Periodic maintenance task that expires old idempotency records and marks stale processing
 * entries as failed.
 */
@Component
public class IdempotencyCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyCleanupTask.class);
    private static final long STALE_THRESHOLD_MINUTES = 30;

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Clock clock;

    public IdempotencyCleanupTask(IdempotencyRecordRepository idempotencyRecordRepository, Clock clock) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredRecords() {
        Instant now = Instant.now(clock);
        int deleted = idempotencyRecordRepository.deleteExpired(now);
        logger.info("Cleaned up {} expired idempotency records", deleted);
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void cleanupStaleProcessing() {
        Instant threshold = Instant.now(clock).minusSeconds(STALE_THRESHOLD_MINUTES * 60);
        int updated = idempotencyRecordRepository.markStaleAsFailed(threshold);
        if (updated > 0) {
            logger.info("Marked {} stale processing records as failed", updated);
        }
    }
}
