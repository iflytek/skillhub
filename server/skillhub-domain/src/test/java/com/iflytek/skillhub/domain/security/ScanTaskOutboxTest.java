package com.iflytek.skillhub.domain.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScanTaskOutboxTest {
    @Test
    void claimAndMarkSentProducesStableTaskPayload() {
        ScanTask task = new ScanTask("task-1", 7L, "/tmp/7", null, "u1", 123L,
                Map.of(
                        "scannerType", ScannerType.SKILL_SCANNER.getValue(),
                        "futureAttribute", "preserved"));
        ScanTaskOutbox outbox = new ScanTaskOutbox(task);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(outbox.claim(now, Duration.ofMinutes(2))).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.SENDING);
        outbox.markSent(now.plusSeconds(1));

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.SENT);
        assertThat(outbox.toScanTask()).isEqualTo(task);
    }

    @Test
    void exhaustedPublishAttemptsMoveTaskToFailed() {
        ScanTaskOutbox outbox = new ScanTaskOutbox(
                new ScanTask("task-failed", 9L, null, "bundle.zip", null, 1L, Map.of()));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        outbox.claim(now, Duration.ofMinutes(2));

        outbox.markFailed(now, "permanent failure");

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLeaseUntil()).isNull();
        assertThat(outbox.claim(now.plusSeconds(1), Duration.ofMinutes(2))).isFalse();
    }

    @Test
    void failedPublishReturnsToPendingWithBackoffAndTruncatesError() {
        ScanTaskOutbox outbox = new ScanTaskOutbox(
                new ScanTask("task-2", 8L, null, "packages/1/8/bundle.zip", null, 1L, Map.of()));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        outbox.claim(now, Duration.ofMinutes(2));
        outbox.markRetry(now, Duration.ofSeconds(5), "x".repeat(5000));

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
    }
}
