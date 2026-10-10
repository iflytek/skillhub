package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.security.ScanTaskOutbox;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxRepository;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
public class ScanTaskOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ScanTaskOutboxDispatcher.class);

    private final ScanTaskOutboxRepository repository;
    private final ScanTaskProducer producer;
    private final SkillVersionRepository versionRepository;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration lease;
    private final Duration maxBackoff;

    public ScanTaskOutboxDispatcher(ScanTaskOutboxRepository repository,
                                    ScanTaskProducer producer,
                                    SkillVersionRepository versionRepository,
                                    Clock clock,
                                    @Value("${skillhub.security.outbox.batch-size:50}") int batchSize,
                                    @Value("${skillhub.security.outbox.max-attempts:10}") int maxAttempts,
                                    @Value("${skillhub.security.outbox.lease:PT2M}") Duration lease,
                                    @Value("${skillhub.security.outbox.max-backoff:PT5M}") Duration maxBackoff) {
        this.repository = repository;
        this.producer = producer;
        this.versionRepository = versionRepository;
        this.clock = clock;
        this.batchSize = batchSize;
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.lease = lease;
        this.maxBackoff = maxBackoff;
    }

    @Scheduled(fixedDelayString = "${skillhub.security.outbox.dispatch-interval-ms:5000}")
    @Transactional
    public void dispatch() {
        Instant now = Instant.now(clock);
        for (ScanTaskOutbox outbox : repository.findDispatchable(now, batchSize)) {
            if (!outbox.claim(now, lease)) {
                continue;
            }
            try {
                producer.publishScanTask(outbox.toScanTask());
                outbox.markSent(Instant.now(clock));
                repository.save(outbox);
            } catch (Exception e) {
                handlePublishFailure(outbox, e);
            }
        }
    }

    private void handlePublishFailure(ScanTaskOutbox outbox, Exception error) {
        Instant now = Instant.now(clock);
        int nextAttempt = outbox.getRetryCount() + 1;
        if (nextAttempt >= maxAttempts) {
            outbox.markFailed(now, error.toString());
            repository.save(outbox);
            versionRepository.findById(outbox.getVersionId())
                    .filter(version -> version.getStatus() == SkillVersionStatus.SCANNING)
                    .ifPresent(version -> {
                        version.setStatus(SkillVersionStatus.SCAN_FAILED);
                        versionRepository.save(version);
                    });
            log.error("Scan task publish failed permanently: taskId={}, versionId={}, attempts={}",
                    outbox.getTaskId(), outbox.getVersionId(), outbox.getRetryCount(), error);
            return;
        }
        Duration delay = retryDelay(nextAttempt);
        outbox.markRetry(now, delay, error.toString());
        repository.save(outbox);
        log.warn("Failed to publish scan task; will retry taskId={}, retryCount={}, nextDelay={}",
                outbox.getTaskId(), outbox.getRetryCount(), delay, error);
    }

    @Scheduled(cron = "0 20 2 * * ?")
    @Transactional
    public void cleanupSent() {
        int deleted = repository.deleteSentBefore(Instant.now(clock).minus(Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Cleaned up {} sent scan outbox records", deleted);
        }
    }

    private Duration retryDelay(int retryCount) {
        long seconds = Math.min(maxBackoff.toSeconds(), 1L << Math.min(retryCount, 16));
        return Duration.ofSeconds(Math.max(seconds, 1));
    }
}
