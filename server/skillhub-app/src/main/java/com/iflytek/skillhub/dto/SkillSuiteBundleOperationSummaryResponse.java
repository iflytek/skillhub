package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;

import java.time.Instant;

/** Active Bundle operation shown before its Suite draft exists. */
public record SkillSuiteBundleOperationSummaryResponse(
        String operationId,
        SkillSuiteBundleMode mode,
        String targetCoordinate,
        String targetVersion,
        SkillSuiteBundleOperationStatus status,
        String failureCode,
        String baseVersion,
        int totalMembers,
        int completedMembers,
        int waitingMembers,
        Instant updatedAt
) {
}
