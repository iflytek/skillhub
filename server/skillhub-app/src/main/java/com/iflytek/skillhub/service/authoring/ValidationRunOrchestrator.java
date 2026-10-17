package com.iflytek.skillhub.service.authoring;

import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.RuntimeBinding;
import com.iflytek.skillhub.domain.authoring.runtime.AssertionEvaluator;
import com.iflytek.skillhub.domain.authoring.runtime.AssertionFailure;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeEventSink;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeExecutionContext;
import com.iflytek.skillhub.domain.authoring.runtime.SkillRuntimeAdapter;
import com.iflytek.skillhub.domain.authoring.runtime.TaskResult;
import com.iflytek.skillhub.domain.authoring.service.DraftStructureValidator;
import com.iflytek.skillhub.domain.authoring.service.RuntimeBindingService;
import com.iflytek.skillhub.domain.authoring.service.RuntimeBindingValidator;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.spec.TaskType;
import com.iflytek.skillhub.domain.authoring.spec.ValidationSpec;
import com.iflytek.skillhub.domain.authoring.spec.ValidationSpecParser;
import com.iflytek.skillhub.domain.authoring.spec.ValidationTaskSpec;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.FindingSeverity;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventType;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.service.authoring.mcp.McpProbeService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Executes validation runs for skill drafts. The STRUCTURE layer validates package
 * layout and SKILL.md frontmatter; the CONFIG layer parses validation.yaml, checks
 * the runtime binding and probes declared MCP servers; the BEHAVIOR layer executes
 * the spec's tasks through the bound runtime adapter and evaluates assertions.
 *
 * <p>Run lifecycle hardening follows the merged upstream review: one FutureTask per
 * run (duplicate submits are ignored), a hard wall-clock watchdog on the timer,
 * cancellation settles first and then interrupts the worker, and the terminal
 * event is appended exactly once.
 */
@Service
public class ValidationRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ValidationRunOrchestrator.class);
    private static final String SPEC_FILE = ValidationSpec.FILE_PATH;
    private static final int MAX_EVENT_TEXT = 4_000;

    private final ValidationRunService runService;
    private final SkillDraftService draftService;
    private final RuntimeBindingService bindingService;
    private final DraftStructureValidator structureValidator;
    private final RuntimeBindingValidator bindingValidator;
    private final McpProbeService mcpProbeService;
    private final ValidationSpecParser specParser = new ValidationSpecParser();
    private final AssertionEvaluator assertionEvaluator = new AssertionEvaluator();
    private final List<SkillRuntimeAdapter> adapters;
    private final ValidationEventBroadcaster broadcaster;
    private final AuthoringProperties properties;
    private final Executor executor;

    private final ScheduledExecutorService timer;
    private final Map<Long, FutureTask<Void>> tasks = new ConcurrentHashMap<>();

    public ValidationRunOrchestrator(ValidationRunService runService,
                                     SkillDraftService draftService,
                                     RuntimeBindingService bindingService,
                                     DraftStructureValidator structureValidator,
                                     RuntimeBindingValidator bindingValidator,
                                     McpProbeService mcpProbeService,
                                     List<SkillRuntimeAdapter> adapters,
                                     ValidationEventBroadcaster broadcaster,
                                     AuthoringProperties properties,
                                     @Qualifier("authoringValidationExecutor") Executor executor,
                                     @Qualifier("authoringValidationTimer") ScheduledExecutorService timer) {
        this.runService = runService;
        this.draftService = draftService;
        this.bindingService = bindingService;
        this.structureValidator = structureValidator;
        this.bindingValidator = bindingValidator;
        this.mcpProbeService = mcpProbeService;
        this.adapters = List.copyOf(adapters);
        this.broadcaster = broadcaster;
        this.properties = properties;
        this.executor = executor;
        this.timer = timer;
    }

    /** Enqueues one QUEUED run for execution; duplicate submits for the same run are ignored. */
    public void submit(Long runId) {
        FutureTask<Void> task = new FutureTask<>(() -> { executeRun(runId); return null; }) {
            @Override protected void done() { tasks.remove(runId, this); }
        };
        if (tasks.putIfAbsent(runId, task) != null) {
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            tasks.remove(runId, task);
            // The run was persisted before enqueue. Settle rather than leave it QUEUED.
            runService.settleIfQueued(runId, 1, 0,
                    Map.of("reason", "validation executor is busy; start a new run"));
            emitTerminalOnce(runId);
        }
    }

    /**
     * Settles cancellation immediately, fences further writes, then interrupts
     * the local worker cooperatively. Interrupt-insensitive work may still finish computing.
     */
    public ValidationRun requestCancel(Long runId, String userId, Set<String> platformRoles) {
        ValidationRun run = runService.requestCancel(runId, userId, platformRoles);
        if (run.isActive()) {
            settle(runId, ValidationRunStatus.CANCELLED, run.getErrorCount(), run.getWarningCount(),
                    Map.of("reason", "cancelled by user"));
            run = runService.getRun(runId);
        } else {
            emitTerminalOnce(runId);
        }
        FutureTask<Void> task = tasks.get(runId);
        if (task != null) { task.cancel(true); }
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
        if (status == ValidationRunStatus.TIMED_OUT) {
            FutureTask<Void> task = tasks.get(runId);
            if (task != null) { task.cancel(true); }
        }
    }

    // ---------------------------------------------------------------- execution

    private void executeRun(Long runId) {
        ScheduledFuture<?> deadline = null;
        boolean claimed = false;
        try {
            ValidationRun run = runService.claimForExecution(runId);
            if (run.getStatus().isTerminal()) {
                emitTerminalOnce(runId);
                return;
            }
            claimed = true;
            deadline = timer.schedule(() -> {
                try {
                    settle(runId, ValidationRunStatus.TIMED_OUT, 0, 0,
                            Map.of("reason", "validation execution deadline exceeded"));
                } catch (RuntimeException exception) {
                    log.error("Could not settle validation deadline for run {}", runId, exception);
                }
            }, Math.max(1, properties.getRunTimeoutMs()), TimeUnit.MILLISECONDS);
            executePipeline(run);
        } catch (DomainConflictException exception) {
            if (claimed) {
                settle(runId, ValidationRunStatus.FAILED, 1, 0,
                        Map.of("reason", "draft revision changed or run already settled"));
            } else {
                // Duplicate submit cannot append a fake terminal to another active worker.
                emitTerminalOnce(runId);
            }
        } catch (Exception exception) {
            log.error("Validation run {} crashed", runId, exception);
            settle(runId, ValidationRunStatus.FAILED, 1, 0,
                    Map.of("internalError", truncate(String.valueOf(exception.getMessage()), 500)));
        } finally {
            if (deadline != null) { deadline.cancel(false); }
        }
    }

    private void executePipeline(ValidationRun run) {
        Long runId = run.getId();
        SkillDraftService.MaterializedSnapshot snapshot =
                draftService.materializeSnapshot(run.getDraftId(), run.getDraftRevision());
        RunContext context = new RunContext(runId);

        emit(runId, ValidationEventType.RUN_STARTED, null, Map.of(
                "draftId", run.getDraftId(),
                "draftName", snapshot.name(),
                "draftRevision", run.getDraftRevision()));

        Path workspace = workspaceFor(runId);
        try {
            // ---- STRUCTURE layer
            emit(runId, ValidationEventType.PHASE_STARTED, "STRUCTURE", Map.of());
            DraftStructureValidator.StructureReport structure =
                    structureValidator.validate(snapshot.name(), snapshot.requirement(), snapshot.entries());
            structure.findings().forEach(context::recordFinding);
            emit(runId, ValidationEventType.PHASE_FINISHED, "STRUCTURE", Map.of(
                    "findings", structure.findings().size(),
                    "errors", context.errorCount(ValidationLayer.STRUCTURE),
                    "warnings", context.warningCount(ValidationLayer.STRUCTURE)));

            // ---- CONFIG layer
            emit(runId, ValidationEventType.PHASE_STARTED, "CONFIG", Map.of());
            ValidationSpec spec = runConfigLayer(context, run, snapshot.entries());
            emit(runId, ValidationEventType.PHASE_FINISHED, "CONFIG", Map.of(
                    "errors", context.errorCount(ValidationLayer.CONFIG),
                    "warnings", context.warningCount(ValidationLayer.CONFIG)));

            // ---- BEHAVIOR layer
            emit(runId, ValidationEventType.PHASE_STARTED, "BEHAVIOR", Map.of());
            boolean blocked = context.errorCount(ValidationLayer.STRUCTURE) > 0
                    || context.errorCount(ValidationLayer.CONFIG) > 0;
            if (blocked) {
                emit(runId, ValidationEventType.PHASE_FINISHED, "BEHAVIOR",
                        Map.of("skipped", true, "reason", "structure or configuration errors"));
            } else {
                materializeWorkspace(workspace, snapshot.entries());
                BehaviorOutcome outcome = runBehaviorLayer(context, run, spec, workspace);
                if (outcome.interrupted()) {
                    return; // already settled (CANCELLED / TIMED_OUT) with terminal event
                }
                emit(runId, ValidationEventType.PHASE_FINISHED, "BEHAVIOR", Map.of(
                        "skipped", false,
                        "tasksRun", context.tasksRun,
                        "tasksFailed", context.tasksFailed));
            }

            settle(runId,
                    context.errorCount == 0 ? ValidationRunStatus.SUCCEEDED : ValidationRunStatus.FAILED,
                    context.errorCount, context.warningCount, summary(context, blocked));
        } catch (DomainConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Validation pipeline for run {} failed", runId, exception);
            settle(runId, ValidationRunStatus.FAILED, context.errorCount + 1, context.warningCount,
                    Map.of("internalError", truncate(String.valueOf(exception.getMessage()), 500)));
        } finally {
            deleteRecursively(workspace);
        }
    }

    // ---------------------------------------------------------------- config layer

    private ValidationSpec runConfigLayer(RunContext context, ValidationRun run,
                                          List<PackageEntry> entries) {
        String specContent = readEntryText(entries, SPEC_FILE);
        ValidationSpec spec = ValidationSpec.empty();
        if (specContent == null) {
            context.recordFinding(FindingDraft.warning(ValidationLayer.CONFIG, "SPEC_MISSING",
                    SPEC_FILE, "validation.yaml is absent; behavior layer runs zero tasks"));
        } else {
            ValidationSpecParser.ParseOutcome outcome = specParser.parse(specContent);
            outcome.errors().forEach(error -> context.recordFinding(FindingDraft.error(
                    ValidationLayer.CONFIG, error.ruleCode(), SPEC_FILE, error.message(), null)));
            if (outcome.isValid()) {
                spec = outcome.spec();
            }
        }

        // The binding is checked (and its MCP servers probed) even when
        // validation.yaml is absent: a binding that cannot work must fail the
        // run here, not surface for the first time at submit.
        RuntimeBinding binding = bindingService.findBinding(run.getDraftId()).orElse(null);
        if (binding == null) {
            if (!spec.safeTasks().isEmpty()) {
                context.recordFinding(FindingDraft.error(ValidationLayer.CONFIG, "BINDING_MISSING",
                        "No runtime binding configured, but validation.yaml declares "
                                + spec.safeTasks().size() + " task(s)"));
            }
            return spec;
        }

        bindingValidator.validate(
                        binding.getAgentType().identifier(),
                        binding.getConfig(),
                        binding.getToolAllowlist(),
                        binding.getMcpServers())
                .forEach(context::recordFinding);

        // declared MCP servers must actually answer before the behavior layer
        // relies on their tools: connect, initialize, tools/list
        List<Map<String, Object>> mcpServers = binding.getMcpServers() == null
                ? List.of() : binding.getMcpServers();
        if (!mcpServers.isEmpty()) {
            McpProbeService.ProbeReport probeReport =
                    mcpProbeService.probe(mcpServers, java.time.Duration.ofSeconds(10));
            probeReport.findings().forEach(context::recordFinding);
            for (McpProbeService.ServerProbe serverProbe : probeReport.servers()) {
                if (serverProbe.connected()) {
                    emit(context.runId, ValidationEventType.LOG, "CONFIG", Map.of(
                            "task", "mcp:" + serverProbe.server(), "stream", "summary",
                            "line", "connected; tools: " + (serverProbe.tools().isEmpty()
                                    ? "(none)" : String.join(", ", serverProbe.tools()))));
                }
            }
        }

        SkillRuntimeAdapter adapter = findAdapter(binding.getAgentType().identifier());
        if (adapter == null) {
            context.recordFinding(FindingDraft.error(ValidationLayer.CONFIG, "ADAPTER_NOT_FOUND",
                    "No runtime adapter is registered for agent type '"
                            + binding.getAgentType().identifier() + "'"));
        } else if (!adapter.enabled()) {
            context.recordFinding(FindingDraft.error(ValidationLayer.CONFIG, "RUNTIME_DISABLED",
                    "Runtime '" + binding.getAgentType().identifier()
                            + "' is disabled in server configuration"));
        }

        for (ValidationTaskSpec task : spec.safeTasks()) {
            assertionEvaluator.validateSyntax(task.safeAssertions(), task.type() == TaskType.PROMPT)
                    .forEach(problem -> context.recordFinding(FindingDraft.error(
                            ValidationLayer.CONFIG, "ASSERTION_INVALID", SPEC_FILE,
                            "task '" + task.name() + "': " + problem, null)));
        }
        return spec;
    }

    // ---------------------------------------------------------------- behavior layer

    private BehaviorOutcome runBehaviorLayer(RunContext context, ValidationRun run,
                                             ValidationSpec spec, Path workspace) {
        RuntimeBinding binding = bindingService.findBinding(run.getDraftId()).orElse(null);
        if (binding == null || spec.safeTasks().isEmpty()) {
            return BehaviorOutcome.COMPLETED;
        }
        SkillRuntimeAdapter adapter = findAdapter(binding.getAgentType().identifier());
        if (adapter == null || !adapter.enabled()) {
            return BehaviorOutcome.COMPLETED; // already reported by the config layer
        }

        long deadline = System.currentTimeMillis() + properties.getRunTimeoutMs();
        Map<String, Object> adapterConfig = binding.getConfig() == null
                ? Map.of() : binding.getConfig();
        List<String> toolAllowlist = binding.getToolAllowlist() == null
                ? List.of() : binding.getToolAllowlist();
        RuntimeExecutionContext executionContext = new RuntimeExecutionContext(
                run.getId(), workspace, adapterConfig, toolAllowlist,
                binding.getMcpServers() == null ? List.of() : binding.getMcpServers(),
                // cancellation settles first and interrupts the worker, so the
                // cooperative signal is the interrupt status of this thread
                () -> Thread.currentThread().isInterrupted());

        for (ValidationTaskSpec task : spec.safeTasks()) {
            if (Thread.currentThread().isInterrupted()) {
                return BehaviorOutcome.interrupted("cancelled");
            }
            if (System.currentTimeMillis() > deadline) {
                settle(run.getId(), ValidationRunStatus.TIMED_OUT,
                        context.errorCount, context.warningCount, summary(context, false));
                return BehaviorOutcome.interrupted("timeout");
            }
            executeTask(context, adapter, executionContext, task, workspace);
        }
        return BehaviorOutcome.COMPLETED;
    }

    private void executeTask(RunContext context, SkillRuntimeAdapter adapter,
                             RuntimeExecutionContext executionContext, ValidationTaskSpec task,
                             Path workspace) {
        if (!adapter.supports(task.type())) {
            context.tasksFailed++;
            context.recordFinding(FindingDraft.error(ValidationLayer.BEHAVIOR, "ADAPTER_TASK_UNSUPPORTED",
                    SPEC_FILE, "runtime '" + adapter.agentType() + "' cannot execute "
                            + task.type() + " task '" + task.name() + "'", null));
            return;
        }
        if (task.type() == TaskType.SCRIPT && !scriptExists(workspace, task.script())) {
            context.tasksFailed++;
            context.recordFinding(FindingDraft.error(ValidationLayer.BEHAVIOR, "TASK_SCRIPT_MISSING",
                    task.script(), "script file not found in the skill: " + task.script(), null));
            return;
        }

        context.tasksRun++;
        TaskResult result;
        try {
            result = adapter.execute(executionContext, task, context.sink());
        } catch (Exception exception) {
            context.tasksFailed++;
            context.recordFinding(FindingDraft.error(ValidationLayer.BEHAVIOR, "TASK_FAILED",
                    SPEC_FILE, "task '" + task.name() + "' failed: "
                            + truncate(String.valueOf(exception.getMessage()), 500), null));
            return;
        }

        List<AssertionFailure> failures =
                assertionEvaluator.evaluate(task.safeAssertions(), result);
        if (failures.isEmpty()) {
            emit(context.runId, ValidationEventType.LOG, "BEHAVIOR", Map.of(
                    "task", task.name(), "stream", "summary",
                    "line", "task '" + task.name() + "' passed "
                            + task.safeAssertions().size() + " assertion(s)"));
            return;
        }
        context.tasksFailed++;
        for (AssertionFailure failure : failures) {
            context.recordFinding(FindingDraft.error(ValidationLayer.BEHAVIOR, "ASSERTION_FAILED",
                    SPEC_FILE, "task '" + task.name() + "': " + failure.describe(), null));
        }
    }

    // ---------------------------------------------------------------- events & terminal handling

    /** Emits one event, persists it, and pushes it to live SSE subscribers. */
    private void emit(Long runId, ValidationEventType type, String phase, Map<String, Object> payload) {
        ValidationEvent event = runService.appendEvent(runId, type, phase, payload);
        broadcaster.publish(runId, event);
    }

    /** Appends the RUN_FINISHED event exactly once per run, then completes SSE subscribers. */
    private void emitTerminalOnce(Long runId) {
        ValidationEvent terminal = runService.appendTerminalEvent(runId);
        if (terminal != null) {
            broadcaster.completeRun(runId, terminal);
        }
    }

    // ---------------------------------------------------------------- small helpers

    private boolean scriptExists(Path workspace, String script) {
        try {
            Path resolved = workspace.resolve(script).normalize();
            return resolved.startsWith(workspace.normalize()) && Files.isRegularFile(resolved);
        } catch (Exception exception) {
            return false;
        }
    }

    private SkillRuntimeAdapter findAdapter(String agentType) {
        return adapters.stream()
                .filter(adapter -> adapter.agentType().equals(agentType))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> summary(RunContext context, boolean behaviorSkipped) {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("structureErrors", context.errorCount(ValidationLayer.STRUCTURE));
        summary.put("structureWarnings", context.warningCount(ValidationLayer.STRUCTURE));
        summary.put("configErrors", context.errorCount(ValidationLayer.CONFIG));
        summary.put("configWarnings", context.warningCount(ValidationLayer.CONFIG));
        summary.put("behaviorTasksRun", context.tasksRun);
        summary.put("behaviorTasksFailed", context.tasksFailed);
        summary.put("behaviorSkipped", behaviorSkipped);
        summary.put("errorCount", context.errorCount);
        summary.put("warningCount", context.warningCount);
        return summary;
    }

    private Path workspaceFor(Long runId) {
        return Path.of(properties.getWorkspaceRoot(), "run-" + runId);
    }

    private void materializeWorkspace(Path workspace, List<PackageEntry> entries)
            throws IOException {
        Files.createDirectories(workspace);
        for (PackageEntry entry : entries) {
            Path target = workspace.resolve(entry.path()).normalize();
            if (!target.startsWith(workspace.normalize())) {
                throw new IOException("package entry escapes the workspace: " + entry.path());
            }
            Files.createDirectories(target.getParent());
            Files.write(target, entry.content());
        }
    }

    private String readEntryText(List<PackageEntry> entries, String path) {
        return entries.stream()
                .filter(entry -> entry.path().equals(path))
                .findFirst()
                .map(entry -> new String(entry.content(), StandardCharsets.UTF_8))
                .orElse(null);
    }

    private void deleteRecursively(Path root) {
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException exception) {
                    log.warn("Could not delete workspace file {}", p, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("Could not clean up workspace {}", root, exception);
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    private record BehaviorOutcome(boolean interrupted, String reason) {

        static BehaviorOutcome COMPLETED = new BehaviorOutcome(false, null);

        static BehaviorOutcome interrupted(String reason) {
            return new BehaviorOutcome(true, reason);
        }
    }

    /** Per-run finding bookkeeping; each finding is persisted and emitted as an event. */
    private final class RunContext {

        private final Long runId;
        private final List<FindingDraft> findings = new ArrayList<>();
        private int errorCount;
        private int warningCount;
        private int tasksRun;
        private int tasksFailed;

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

        /** Serialized sink so per-run event sequence numbers cannot race. */
        RuntimeEventSink sink() {
            return new RuntimeEventSink() {
                @Override
                public synchronized void agentMessage(String taskName, String content) {
                    emit(runId, ValidationEventType.AGENT_MESSAGE, "BEHAVIOR", Map.of(
                            "task", taskName, "content", truncate(content, MAX_EVENT_TEXT)));
                }

                @Override
                public synchronized void toolCall(String taskName, String tool, String argumentsJson) {
                    emit(runId, ValidationEventType.TOOL_CALL, "BEHAVIOR", Map.of(
                            "task", taskName, "tool", tool,
                            "arguments", truncate(argumentsJson, MAX_EVENT_TEXT)));
                }

                @Override
                public synchronized void toolResult(String taskName, String tool, String summaryJson) {
                    emit(runId, ValidationEventType.TOOL_RESULT, "BEHAVIOR", Map.of(
                            "task", taskName, "tool", tool,
                            "summary", truncate(summaryJson, MAX_EVENT_TEXT)));
                }

                @Override
                public synchronized void log(String taskName, String stream, String line) {
                    emit(runId, ValidationEventType.LOG, "BEHAVIOR", Map.of(
                            "task", taskName, "stream", stream,
                            "line", truncate(line, 2_000)));
                }
            };
        }
    }
}
