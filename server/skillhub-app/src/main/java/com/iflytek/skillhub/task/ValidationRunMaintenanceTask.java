package com.iflytek.skillhub.task;

import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.service.authoring.ValidationRunOrchestrator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bounded recovery for validation runs: active runs older than the configured stale
 * threshold are settled as TIMED_OUT (covering executor crashes and restarts), and
 * orphaned workspace directories are removed best-effort.
 */
@Component
public class ValidationRunMaintenanceTask {

    private static final Logger log = LoggerFactory.getLogger(ValidationRunMaintenanceTask.class);

    private final ValidationRunService runService;
    private final ValidationRunOrchestrator orchestrator;
    private final AuthoringProperties properties;

    public ValidationRunMaintenanceTask(ValidationRunService runService,
                                        ValidationRunOrchestrator orchestrator,
                                        AuthoringProperties properties) {
        this.runService = runService;
        this.orchestrator = orchestrator;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${skillhub.authoring.maintenance-interval-ms:30000}")
    public void sweep() {
        settleStaleRuns();
        cleanupWorkspaces();
    }

    private void settleStaleRuns() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(properties.getStaleRunMinutes()));
        List<ValidationRun> stale = runService.findActiveRunsCreatedBefore(cutoff);
        for (ValidationRun run : stale) {
            log.warn("Settling stale validation run {} (draft {}, status {}) as TIMED_OUT",
                    run.getId(), run.getDraftId(), run.getStatus());
            orchestrator.settle(run.getId(), ValidationRunStatus.TIMED_OUT,
                    run.getErrorCount(), run.getWarningCount(),
                    Map.of("reason", "stale run swept by maintenance task"));
        }
    }

    /** Removes workspace directories of runs that are no longer active. */
    private void cleanupWorkspaces() {
        Path root = Path.of(properties.getWorkspaceRoot());
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var directories = Files.list(root)) {
            directories.filter(Files::isDirectory)
                    .filter(directory -> directory.getFileName().toString().startsWith("run-"))
                    .forEach(this::cleanupIfOrphaned);
        } catch (IOException exception) {
            log.warn("Failed to list validation workspaces under {}: {}", root, exception.getMessage());
        }
    }

    private void cleanupIfOrphaned(Path directory) {
        String fileName = directory.getFileName().toString();
        long runId;
        try {
            runId = Long.parseLong(fileName.substring("run-".length()));
        } catch (NumberFormatException exception) {
            return;
        }
        try {
            ValidationRun run = runService.getRun(runId);
            if (run.isActive()) {
                return;
            }
            try (var stream = Files.walk(directory)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        } catch (Exception exception) {
            // unknown run id (workspace of a deleted draft) — remove the directory too
            deleteQuietly(directory);
        }
    }

    private void deleteQuietly(Path directory) {
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
