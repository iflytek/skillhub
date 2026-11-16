package com.iflytek.skillhub.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskOutbox;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxRepository;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxStatus;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    void publisherFailureDoesNotPersistOrLogExceptionContent() {
        String marker = "SKILL_BODY_AND_TOKEN_SENTINEL";
        ScanTaskOutbox outbox = outbox("task-private-error", 7L);
        SkillVersion version = new SkillVersion(9L, "1.0.0", "user");
        version.setStatus(SkillVersionStatus.SCANNING);
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));
        given(versionRepository.findById(7L)).willReturn(Optional.of(version));
        doThrow(new IllegalStateException(marker)).when(producer).publishScanTask(any());
        Logger logger = (Logger) LoggerFactory.getLogger(ScanTaskOutboxDispatcher.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            dispatcher(2).dispatch();
            assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.PENDING);
            assertThat(ReflectionTestUtils.getField(outbox, "lastError"))
                    .isEqualTo("SCAN_TASK_PUBLISH_FAILED:IllegalStateException");

            dispatcher(2).dispatch();
            assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.FAILED);
            assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.SCAN_FAILED);
            assertThat(ReflectionTestUtils.getField(outbox, "lastError"))
                    .isEqualTo("SCAN_TASK_PUBLISH_FAILED:IllegalStateException");
            String logText = logs.list.stream().map(event -> event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                    .collect(Collectors.joining("\n"));
            assertThat(logText).doesNotContain(marker);
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

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
    void lastPublishAttemptSettlesTheScanAttemptThroughTheScanFailurePath() {
        ScanTaskOutbox outbox = outbox("task-3", 3L);
        SecurityScanService scanFailures = org.mockito.Mockito.mock(SecurityScanService.class);
        given(repository.findDispatchable(any(), any(Integer.class))).willReturn(List.of(outbox));
        given(scanFailures.processUndispatchableScan(org.mockito.ArgumentMatchers.eq("task-3"),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(ScannerType.SKILL_SCANNER),
                any())).willReturn(true);
        doThrow(new IllegalStateException("redis unavailable")).when(producer).publishScanTask(any());
        ScanTaskOutboxDispatcher dispatcher = dispatcher(1);
        dispatcher.setScanFailures(scanFailures);

        dispatcher.dispatch();

        assertThat(outbox.getStatus()).isEqualTo(ScanTaskOutboxStatus.FAILED);
        verify(scanFailures).processUndispatchableScan(org.mockito.ArgumentMatchers.eq("task-3"),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(ScannerType.SKILL_SCANNER),
                org.mockito.ArgumentMatchers.startsWith("SCAN_TASK_PUBLISH_FAILED:"));
        verifyNoInteractions(versionRepository);
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
