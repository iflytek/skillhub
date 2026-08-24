package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.security.ScanTaskOutbox;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxRepository;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
public class ScanTaskOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ScanTaskOutboxDispatcher.class);

    private final ScanTaskOutboxRepository repository;
    private final ScanTaskProducer producer;
    private final Clock clock;
    private final int batchSize;
    private final Duration lease;
    private final Duration maxBackoff;

    public ScanTaskOutboxDispatcher(ScanTaskOutboxRepository repository,
                                    ScanTaskProducer producer,
                                    Clock clock,
                                    @Value("${skillhub.security.outbox.batch-size:50}") int batchSize,
                                    @Value("${skillhub.security.outbox.lease:PT2M}") Duration lease,
                                    @Value("${skillhub.security.outbox.max-backoff:PT5M}") Duration maxBackoff) {
        this.repository = repository;
        this.producer = producer;
        this.clock = clock;
        this.batchSize = batchSize;
        this.lease = lease;
        this.maxBackoff = maxBackoff;
    }

    @Scheduled(fixedDelayString = "${skillhub.security.outbox.dispatch-interval-ms:5000}")
    @Transactional
    public void dispatch() {
        Instant now = Instant.now(clock);
        Map<String, ScanTaskOutbox> candidates = new LinkedHashMap<>();
        repository.findPendingDue(now, batchSize).forEach(o -> candidates.put(o.getTaskId(), o));
        repository.findExpiredLeases(now, batchSize).forEach(o -> candidates.put(o.getTaskId(), o));
        for (ScanTaskOutbox outbox : candidates.values()) {
            if (!outbox.claim(now, lease)) continue;
            repository.saveAndFlush(outbox);
            try {
                producer.publishScanTask(outbox.toScanTask());
                outbox.markSent(Instant.now(clock));
                repository.save(outbox);
            } catch (Exception e) {
                Duration delay = retryDelay(outbox.getRetryCount() + 1);
                outbox.markRetry(Instant.now(clock), delay, e.toString());
                repository.save(outbox);
                log.warn("Failed to publish scan task; will retry taskId={}, retryCount={}, nextDelay={}",
                        outbox.getTaskId(), outbox.getRetryCount(), delay, e);
            }
        }
    }

    @Scheduled(cron = "0 20 2 * * ?")
    @Transactional
    public void cleanupSent() {
        int deleted = repository.deleteSentBefore(Instant.now(clock).minus(Duration.ofDays(7)));
        if (deleted > 0) log.info("Cleaned up {} sent scan outbox records", deleted);
    }

    private Duration retryDelay(int retryCount) {
        long seconds = Math.min(maxBackoff.toSeconds(), 1L << Math.min(retryCount, 16));
        return Duration.ofSeconds(Math.max(seconds, 1));
    }
}