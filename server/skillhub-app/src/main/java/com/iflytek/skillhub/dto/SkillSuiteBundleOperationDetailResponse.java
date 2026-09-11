package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;

import java.time.Instant;
import java.util.List;

public record SkillSuiteBundleOperationDetailResponse(
        String operationId,
        SkillSuiteBundleOperationStatus status,
        SkillSuiteBundleMode mode,
        String targetCoordinate,
        Long targetNamespaceId,
        Long targetSuiteId,
        String targetVersion,
        String failureCode,
        Long resultSuiteId,
        Long resultSuiteVersionId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<OperationMember> members
) {
    public record OperationMember(
            int position,
            boolean redacted,
            String coordinate,
            SkillSuiteBundleMemberSourceType sourceType,
            SkillSuiteBundleRelationshipChange relationship,
            SkillSuiteBundlePublishAction publishAction,
            SkillSuiteBundleMemberResultStatus status,
            SkillVisibility visibility,
            String version,
            Long skillId,
            Long skillVersionId,
            List<String> errors,
            List<String> warnings
    ) {
    }
}
