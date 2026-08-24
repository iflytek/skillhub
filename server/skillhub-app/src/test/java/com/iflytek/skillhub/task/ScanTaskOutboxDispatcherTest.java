package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskOutbox;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxRepository;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScanTaskOutboxDispatcherTest {
    @Mock ScanTaskOutboxRepository repository;
    @Mock ScanTaskProducer producer;

    @Test
    void failedRedisPublishLeavesTaskPendingForRetry() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        ScanTaskOutbox outbox = new ScanTaskOutbox(
                new ScanTask("task-1", 1L, "/tmp/1", null, "user", 1L,
                        java.util.Map.of("scannerType", ScannerType.SKILL_SCANNER.getValue())));
        given(repository.findPendingDue(any(), any(Integer.class))).willReturn(List.of(outbox));
        given(repository.findExpiredLeases(any(), any(Integer.class))).willReturn(List.of());
        doThrow(new IllegalStateException("redis unavailable")).when(producer).publishScanTask(any());
        ScanTaskOutboxDispatcher dispatcher = new ScanTaskOutboxDispatcher(
                repository, producer, clock, 50, Duration.ofMinutes(2), Duration.ofMinutes(5));

        dispatcher.dispatch();

        assertThat(outbox.getStatus()).isEqualTo(com.iflytek.skillhub.domain.security.ScanTaskOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        verify(producer).publishScanTask(any());
        verify(repository).saveAndFlush(outbox);
    }
}