package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskOutbox;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxRepository;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxStatus;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ScanTaskOutboxDispatcherTest {
    @Mock ScanTaskOutboxRepository repository;
    @Mock ScanTaskProducer producer;
    @Mock SkillVersionRepository versionRepository;

    @Test
    void failedRedisPublishLeavesTaskPendingForRetry() {
        ScanTaskOutbox outbox = outbox("task-1", 1L);
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));
        doThrow(new IllegalStateException("redis unavailable")).when(producer).publishScanTask(any());

        dispatcher(10).dispatch();

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        verify(producer).publishScanTask(any());
        verify(repository).save(outbox);
    }

    @Test
    void successfulPublishMarksTaskSentWithoutChangingVersion() {
        ScanTaskOutbox outbox = outbox("task-success", 3L);
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));

        dispatcher(10).dispatch();

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.SENT);
        assertThat(outbox.getRetryCount()).isZero();
        verify(producer).publishScanTask(any());
        verify(repository).save(outbox);
        verifyNoInteractions(versionRepository);
    }

    @Test
    void lastPublishAttemptMarksOutboxAndVersionFailed() {
        ScanTaskOutbox outbox = outbox("task-2", 2L);
        SkillVersion version = new SkillVersion(9L, "1.0.0", "user");
        version.setStatus(SkillVersionStatus.SCANNING);
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));
        given(versionRepository.findById(2L)).willReturn(Optional.of(version));
        doThrow(new IllegalStateException("redis unavailable")).when(producer).publishScanTask(any());

        dispatcher(1).dispatch();

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.FAILED);
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCAN_FAILED);
        verify(versionRepository).save(version);
    }

    @Test
    void lastPublishAttemptDoesNotOverwriteTerminalVersionStatus() {
        ScanTaskOutbox outbox = outbox("task-published", 4L);
        SkillVersion version = new SkillVersion(9L, "1.0.0", "user");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));
        given(versionRepository.findById(4L)).willReturn(Optional.of(version));
        doThrow(new IllegalStateException("redis unavailable")).when(producer).publishScanTask(any());

        dispatcher(1).dispatch();

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.FAILED);
        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        verify(versionRepository, never()).save(version);
    }

    @Test
    void expiredLeaseCanBeReclaimedAndPublished() {
        ScanTaskOutbox outbox = outbox("task-expired", 5L);
        assertThat(outbox.claim(Instant.parse("2025-12-31T23:00:00Z"), Duration.ofMinutes(2))).isTrue();
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));

        dispatcher(10).dispatch();

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.SENT);
        verify(producer).publishScanTask(any());
    }

    @Test
    void staleFinderResultInTerminalStateIsIgnored() {
        ScanTaskOutbox outbox = outbox("task-sent", 6L);
        outbox.markSent(Instant.parse("2025-12-31T23:00:00Z"));
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));

        dispatcher(10).dispatch();

        verifyNoInteractions(producer);
        verify(repository, never()).save(outbox);
    }

    @Test
    void maxAttemptsMustBePositive() {
        assertThatThrownBy(() -> dispatcher(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    private ScanTaskOutboxDispatcher dispatcher(int maxAttempts) {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        return new ScanTaskOutboxDispatcher(repository, producer, versionRepository, clock,
                50, maxAttempts, Duration.ofMinutes(2), Duration.ofMinutes(5));
    }

    private ScanTaskOutbox outbox(String taskId, Long versionId) {
        return new ScanTaskOutbox(new ScanTask(taskId, versionId, "/tmp/" + versionId, null, "user", 1L,
                java.util.Map.of("scannerType", ScannerType.SKILL_SCANNER.getValue())));
    }
}
