package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import java.time.Instant;
import java.util.Map;

/** Validation run state and outcome counters. */
public record ValidationRunResponse(
        Long id,
        Long draftId,
        Integer draftRevision,
        String status,
        boolean cancelRequested,
        boolean active,
        boolean terminal,
        Integer errorCount,
        Integer warningCount,
        String triggeredBy,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Map<String, Object> summary
) {

    public static ValidationRunResponse from(ValidationRun run) {
        return from(run, run.getSummary());
    }

    public static ValidationRunResponse from(ValidationRun run, Map<String, Object> summary) {
        return new ValidationRunResponse(
                run.getId(),
                run.getDraftId(),
                run.getDraftRevision(),
                run.getStatus().name(),
                run.isCancelRequested(),
                run.isActive(),
                run.getStatus().isTerminal(),
                run.getErrorCount(),
                run.getWarningCount(),
                run.getTriggeredBy(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                summary == null ? Map.of() : summary);
    }
}
