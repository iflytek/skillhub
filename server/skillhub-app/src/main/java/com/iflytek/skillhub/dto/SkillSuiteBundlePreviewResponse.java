package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;

import java.time.Instant;
import java.util.List;

public record SkillSuiteBundlePreviewResponse(
        String previewToken,
        Instant expiresAt,
        boolean confirmable,
        Target target,
        List<PreviewMember> members,
        List<RemovedMember> removedMembers,
        List<String> errors,
        List<String> warnings,
        String warningDigest
) {
    public record Target(
            SkillSuiteBundleMode mode,
            String coordinate,
            Long namespaceId,
            Long suiteId,
            Long baseSuiteVersionId,
            String targetVersion,
            String displayName,
            String summary,
            String overview,
            SkillVisibility visibility
    ) {
    }

    public record PreviewMember(
            String coordinate,
            SkillSuiteBundleMemberSourceType sourceType,
            SkillSuiteBundleRelationshipChange relationship,
            SkillSuiteBundlePublishAction publishAction,
            Long skillId,
            Long skillVersionId,
            SkillVisibility finalVisibility,
            String resolvedVersion,
            String fingerprint,
            List<String> errors,
            List<String> warnings
    ) {
    }

    public record RemovedMember(
            String coordinate,
            Long skillId,
            Long skillVersionId,
            String version,
            boolean entry
    ) {
    }
}
