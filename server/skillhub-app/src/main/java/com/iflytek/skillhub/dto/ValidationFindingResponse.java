package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.authoring.FixSuggestion;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import java.time.Instant;

/**
 * One validation finding. {@code suggestion} carries the machine-applicable fix
 * (patches with old/new values) when one exists, for preview and confirmed apply.
 */
public record ValidationFindingResponse(
        Long id,
        Long runId,
        String layer,
        String ruleCode,
        String severity,
        String filePath,
        String location,
        String message,
        FixSuggestion suggestion,
        String status,
        Integer appliedRevision,
        Instant createdAt
) {

    public static ValidationFindingResponse from(ValidationFinding finding) {
        return new ValidationFindingResponse(
                finding.getId(),
                finding.getRunId(),
                finding.getLayer().name(),
                finding.getRuleCode(),
                finding.getSeverity().name(),
                finding.getFilePath(),
                finding.getLocation(),
                finding.getMessage(),
                finding.getSuggestion(),
                finding.getStatus().name(),
                finding.getAppliedRevision(),
                finding.getCreatedAt());
    }
}
