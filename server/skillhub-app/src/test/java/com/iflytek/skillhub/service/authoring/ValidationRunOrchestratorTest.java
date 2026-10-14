package com.iflytek.skillhub.service.authoring;

import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.service.DraftStructureValidator;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventType;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ValidationRunOrchestratorTest {
    private final ValidationRunService runs = mock(ValidationRunService.class);
    private final SkillDraftService drafts = mock(SkillDraftService.class);
    private final DraftStructureValidator validator = mock(DraftStructureValidator.class);
    private final ValidationEventBroadcaster broadcaster = mock(ValidationEventBroadcaster.class);
    private final com.iflytek.skillhub.domain.authoring.service.RuntimeBindingService bindings =
            mock(com.iflytek.skillhub.domain.authoring.service.RuntimeBindingService.class);
    private final com.iflytek.skillhub.domain.authoring.service.RuntimeBindingValidator bindingValidator =
            mock(com.iflytek.skillhub.domain.authoring.service.RuntimeBindingValidator.class);
    private final com.iflytek.skillhub.service.authoring.mcp.McpProbeService mcpProbeService =
            mock(com.iflytek.skillhub.service.authoring.mcp.McpProbeService.class);

    @Test
    void duplicateClaimCannotEmitAnActiveRunAsFinished() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try {
            when(runs.claimForExecution(1L)).thenThrow(new DomainConflictException("already running"));
            when(runs.appendTerminalEvent(1L)).thenReturn(null);
            orchestrator(Runnable::run, timer, 1000).submit(1L);
            verify(broadcaster, never()).completeRun(anyLong(), any());
            verify(runs, never()).appendEvent(anyLong(), eq(ValidationEventType.RUN_FINISHED), any(), any());
        } finally { timer.shutdownNow(); }
    }

    @Test
    void rejectionSettlesOnlyQueuedRunsWithoutExecutingOnRequestThread() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try {
            orchestrator(task -> { throw new RejectedExecutionException(); }, timer, 1000).submit(1L);
            verify(runs).settleIfQueued(eq(1L), eq(1), eq(0), anyMap());
            verify(runs, never()).claimForExecution(anyLong());
            verify(runs, never()).settleIfActive(anyLong(), any(), anyInt(), anyInt(), anyMap());
        } finally { timer.shutdownNow(); }
    }

    @Test
    void executionDeadlineSettlesEvenWhenValidatorIgnoresInterrupts() throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch validating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch timedOut = new CountDownLatch(1);
        CountDownLatch workerEnded = new CountDownLatch(1);
        AtomicBoolean terminal = new AtomicBoolean();
        ValidationRun run = new ValidationRun(2L, 1, "owner");
        ReflectionTestUtils.setField(run, "id", 1L);
        run.markPreparing();
        run.markRunning(Instant.now());
        when(runs.claimForExecution(1L)).thenReturn(run);
        when(drafts.materializeSnapshot(2L, 1)).thenReturn(
                new SkillDraftService.MaterializedSnapshot("test", "requirement", List.of()));
        when(runs.appendEvent(eq(1L), any(), any(), anyMap())).thenAnswer(invocation -> {
            if (terminal.get()) {
                workerEnded.countDown();
                throw new DomainConflictException("already terminal");
            }
            return new ValidationEvent(1L, 1, invocation.getArgument(1), null, Map.of());
        });
        doAnswer(invocation -> {
            terminal.set(true);
            timedOut.countDown();
            return java.util.Optional.empty();
        }).when(runs).settleIfActive(eq(1L), eq(ValidationRunStatus.TIMED_OUT), anyInt(), anyInt(), anyMap());
        when(validator.validate(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            validating.countDown();
            boolean released = false;
            while (!released) {
                try { released = release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* intentionally uncooperative validator */ }
            }
            return new DraftStructureValidator.StructureReport(List.of(), true);
        });
        try {
            orchestrator(executor, timer, 100).submit(1L);
            assertThat(validating.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(timedOut.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(workerEnded.await(2, TimeUnit.SECONDS)).isTrue();
            verify(runs, never()).recordFinding(anyLong(), any());
            verify(runs, never()).settleIfActive(eq(1L), eq(ValidationRunStatus.SUCCEEDED), anyInt(), anyInt(), anyMap());
        } finally { release.countDown(); executor.shutdownNow(); timer.shutdownNow(); }
    }

    @Test
    void snapshotRevisionConflictAfterClaimSettlesInsteadOfLeavingRunning() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try {
            ValidationRun run = new ValidationRun(2L, 1, "owner");
            ReflectionTestUtils.setField(run, "id", 1L);
            run.markPreparing();
            run.markRunning(Instant.now());
            when(runs.claimForExecution(1L)).thenReturn(run);
            when(drafts.materializeSnapshot(2L, 1)).thenThrow(new DomainConflictException("revision changed"));
            orchestrator(Runnable::run, timer, 1000).submit(1L);
            verify(runs).settleIfActive(eq(1L), eq(ValidationRunStatus.FAILED), eq(1), eq(0), anyMap());
        } finally { timer.shutdownNow(); }
    }

    @Test
    void completedCancelDoesNotAllocatePermanentControlState() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try {
            ValidationRun run = new ValidationRun(2L, 1, "owner");
            run.finish(ValidationRunStatus.SUCCEEDED, 0, 0, Map.of(), Instant.now());
            when(runs.requestCancel(eq(1L), eq("owner"), anySet())).thenReturn(run);
            ValidationRunOrchestrator orchestrator = orchestrator(Runnable::run, timer, 1000);
            orchestrator.requestCancel(1L, "owner", java.util.Set.of());
            assertThat((Map<?, ?>) ReflectionTestUtils.getField(orchestrator, "tasks")).isEmpty();
            verify(runs, never()).settleIfActive(anyLong(), any(), anyInt(), anyInt(), anyMap());
        } finally { timer.shutdownNow(); }
    }

    private ValidationRunOrchestrator orchestrator(java.util.concurrent.Executor executor,
                                                   ScheduledExecutorService timer, long timeout) {
        AuthoringProperties properties = new AuthoringProperties();
        properties.setRunTimeoutMs(timeout);
        return new ValidationRunOrchestrator(runs, drafts, bindings, validator, bindingValidator,
                mcpProbeService, java.util.List.of(), broadcaster, properties, executor, timer);
    }
}
