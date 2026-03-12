package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class IdempotencyCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyCleanupTask.class);
    private static final long STALE_THRESHOLD_MINUTES = 30;

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyCleanupTask(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredRecords() {
        Instant now = Instant.now();
        int deleted = idempotencyRecordRepository.deleteExpired(now);
        logger.info("Cleaned up {} expired idempotency records", deleted);
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void cleanupStaleProcessing() {
        Instant threshold = Instant.now().minusSeconds(STALE_THRESHOLD_MINUTES * 60);
        int updated = idempotencyRecordRepository.markStaleAsFailed(threshold);
        if (updated > 0) {
            logger.info("Marked {} stale processing records as failed", updated);
        }
    }
}
