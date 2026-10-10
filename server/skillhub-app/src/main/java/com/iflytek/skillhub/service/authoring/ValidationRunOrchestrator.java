package com.iflytek.skillhub.service.authoring;

import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.DraftStructureValidator;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.FindingSeverity;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventType;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Executes validation runs for skill drafts. This layer runs the STRUCTURE
 * phase: package layout, SKILL.md frontmatter and file limits, reported as
 * structured findings with fix suggestions. The CONFIG and BEHAVIOR phases
 * (runtime binding, MCP probing, script/LLM task execution) arrive with the
 * runtime layer.
 */
@Service
public class ValidationRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ValidationRunOrchestrator.class);
    private static final int MAX_EVENT_TEXT = 4_000;

    private final ValidationRunService runService;
    private final SkillDraftService draftService;
    private final DraftStructureValidator structureValidator;
    private final ValidationEventBroadcaster broadcaster;
    private final Executor executor;

    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /** Serializes the RUN_FINISHED emission so it happens exactly once per run. */
    private final Map<Long, Object> terminalLocks = new ConcurrentHashMap<>();

    public ValidationRunOrchestrator(ValidationRunService runService,
                                     SkillDraftService draftService,
                                     DraftStructureValidator structureValidator,
                                     ValidationEventBroadcaster broadcaster,
                                     @Qualifier("authoringValidationExecutor") Executor executor) {
        this.runService = runService;
        this.draftService = draftService;
        this.structureValidator = structureValidator;
        this.broadcaster = broadcaster;
        this.executor = executor;
    }

    /** Enqueues one QUEUED run for execution. */
    public void submit(Long runId) {
        executor.execute(() -> executeRun(runId));
    }

    /**
     * Requests cooperative cancellation. QUEUED runs settle immediately; running runs
     * settle when their executor observes the flag.
     */
    public ValidationRun requestCancel(Long runId, String userId, Set<String> platformRoles) {
        ValidationRun run = runService.requestCancel(runId, userId, platformRoles);
        cancelFlags.computeIfAbsent(runId, key -> new AtomicBoolean()).set(true);
        if (run.getStatus().isTerminal()) {
            emitTerminalOnce(runId);
        }
        return run;
    }

    /**
     * Idempotent terminal settle: persists the outcome (first writer wins), appends the
     * RUN_FINISHED event exactly once, and completes SSE subscribers.
     */
    public void settle(Long runId, ValidationRunStatus status, int errorCount, int warningCount,
                       Map<String, Object> summary) {
        runService.settleIfActive(runId, status, errorCount, warningCount, summary);
        emitTerminalOnce(runId);
    }

    // ---------------------------------------------------------------- execution

    private void executeRun(Long runId) {
        try {
            ValidationRun run = runService.claimForExecution(runId);
            if (run.getStatus().isTerminal()) {
                emitTerminalOnce(runId);
                return;
            }
            executePipeline(run);
        } catch (DomainConflictException exception) {
            // cancelled while queued, or already claimed/settled elsewhere
            log.info("Validation run {} no longer claimable: {}", runId, exception.getMessage());
            emitTerminalOnce(runId);
        } catch (Exception exception) {
            log.error("Validation run {} crashed", runId, exception);
            settle(runId, ValidationRunStatus.FAILED, 1, 0,
                    Map.of("internalError", truncate(String.valueOf(exception.getMessage()), 500)));
        } finally {
            cancelFlags.remove(runId);
            terminalLocks.remove(runId);
        }
    }

    private void executePipeline(ValidationRun run) {
        Long runId = run.getId();
        SkillDraft draft = draftService.getDraft(run.getDraftId());
        List<PackageEntry> entries = draftService.materializeEntries(draft.getId());
        RunContext context = new RunContext(runId);

        emit(runId, ValidationEventType.RUN_STARTED, null, Map.of(
                "draftId", draft.getId(),
                "draftName", draft.getName(),
                "draftRevision", run.getDraftRevision()));

        try {
            // ---- STRUCTURE layer
            emit(runId, ValidationEventType.PHASE_STARTED, "STRUCTURE", Map.of());
            DraftStructureValidator.StructureReport structure =
                    structureValidator.validate(draft.getName(), draft.getRequirement(), entries);
            structure.findings().forEach(context::recordFinding);
            emit(runId, ValidationEventType.PHASE_FINISHED, "STRUCTURE", Map.of(
                    "findings", structure.findings().size(),
                    "errors", context.errorCount(ValidationLayer.STRUCTURE),
                    "warnings", context.warningCount(ValidationLayer.STRUCTURE)));

            if (cancelFlags.getOrDefault(runId, new AtomicBoolean(false)).get()) {
                settle(runId, ValidationRunStatus.CANCELLED, context.errorCount, context.warningCount,
                        summary(context));
                return;
            }

            settle(runId,
                    context.errorCount == 0 ? ValidationRunStatus.SUCCEEDED : ValidationRunStatus.FAILED,
                    context.errorCount, context.warningCount, summary(context));
        } catch (Exception exception) {
            log.error("Validation pipeline for run {} failed", runId, exception);
            settle(runId, ValidationRunStatus.FAILED, context.errorCount + 1, context.warningCount,
                    Map.of("internalError", truncate(String.valueOf(exception.getMessage()), 500)));
        }
    }

    // ---------------------------------------------------------------- events

    private void emit(Long runId, ValidationEventType type, String phase, Map<String, Object> payload) {
        ValidationEvent event = runService.appendEvent(runId, type, phase, payload);
        broadcaster.publish(runId, event);
    }

    /** Appends the RUN_FINISHED event exactly once per run, then completes SSE subscribers. */
    private void emitTerminalOnce(Long runId) {
        Object lock = terminalLocks.computeIfAbsent(runId, key -> new Object());
        synchronized (lock) {
            for (ValidationEvent event : runService.listEvents(runId, null)) {
                if (event.getEventType() == ValidationEventType.RUN_FINISHED) {
                    broadcaster.completeRun(runId, event);
                    return;
                }
            }
            ValidationRun run = runService.getRun(runId);
            ValidationEvent terminal = runService.appendEvent(runId,
                    ValidationEventType.RUN_FINISHED, null, Map.of(
                            "status", run.getStatus().name(),
                            "errorCount", run.getErrorCount(),
                            "warningCount", run.getWarningCount()));
            broadcaster.completeRun(runId, terminal);
        }
    }

    // ---------------------------------------------------------------- small helpers

    private Map<String, Object> summary(RunContext context) {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("structureErrors", context.errorCount(ValidationLayer.STRUCTURE));
        summary.put("structureWarnings", context.warningCount(ValidationLayer.STRUCTURE));
        summary.put("errorCount", context.errorCount);
        summary.put("warningCount", context.warningCount);
        return summary;
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    /** Per-run finding bookkeeping; each finding is persisted and emitted as an event. */
    private final class RunContext {

        private final Long runId;
        private final List<FindingDraft> findings = new ArrayList<>();
        private int errorCount;
        private int warningCount;

        RunContext(Long runId) {
            this.runId = runId;
        }

        int errorCount(ValidationLayer layer) {
            return (int) findings.stream()
                    .filter(finding -> finding.layer() == layer
                            && finding.severity() == FindingSeverity.ERROR)
                    .count();
        }

        int warningCount(ValidationLayer layer) {
            return (int) findings.stream()
                    .filter(finding -> finding.layer() == layer
                            && finding.severity() == FindingSeverity.WARNING)
                    .count();
        }

        void recordFinding(FindingDraft draft) {
            findings.add(draft);
            if (draft.severity() == FindingSeverity.ERROR) {
                errorCount++;
            } else if (draft.severity() == FindingSeverity.WARNING) {
                warningCount++;
            }
            ValidationFinding persisted = runService.recordFinding(runId, draft);
            emit(runId, ValidationEventType.FINDING, draft.layer().name(), Map.of(
                    "findingId", persisted.getId(),
                    "layer", draft.layer().name(),
                    "ruleCode", draft.ruleCode(),
                    "severity", draft.severity().name(),
                    "filePath", draft.filePath() == null ? "" : draft.filePath(),
                    "message", truncate(draft.message(), MAX_EVENT_TEXT),
                    "hasSuggestion", draft.suggestion() != null));
        }
    }
}
