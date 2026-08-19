package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SkillSummaryResponse(
        Long id,
        String slug,
        String displayName,
        String summary,
        String visibility,
        String status,
        Long downloadCount,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String namespace,
        Instant updatedAt,
        boolean canSubmitPromotion,
        SkillLifecycleVersionResponse headlineVersion,
        SkillLifecycleVersionResponse publishedVersion,
        SkillLifecycleVersionResponse ownerPreviewVersion,
        String resolutionMode,
        ComplianceSnapshotResponse complianceSnapshot,
        /**
         * Labels attached to the skill, present only when the caller asked for them.
         * Left out of the payload otherwise, so responses are unchanged for callers
         * that do not opt in.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<SkillLabelDto> labels
) {

    /**
     * Summary without label projection.
     */
    public SkillSummaryResponse(
            Long id,
            String slug,
            String displayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            BigDecimal ratingAvg,
            Integer ratingCount,
            String namespace,
            Instant updatedAt,
            boolean canSubmitPromotion,
            SkillLifecycleVersionResponse headlineVersion,
            SkillLifecycleVersionResponse publishedVersion,
            SkillLifecycleVersionResponse ownerPreviewVersion,
            String resolutionMode,
            ComplianceSnapshotResponse complianceSnapshot) {
        this(id, slug, displayName, summary, visibility, status, downloadCount, starCount, ratingAvg,
                ratingCount, namespace, updatedAt, canSubmitPromotion, headlineVersion, publishedVersion,
                ownerPreviewVersion, resolutionMode, complianceSnapshot, null);
    }

    public SkillSummaryResponse withLabels(List<SkillLabelDto> labels) {
        return new SkillSummaryResponse(id, slug, displayName, summary, visibility, status, downloadCount,
                starCount, ratingAvg, ratingCount, namespace, updatedAt, canSubmitPromotion, headlineVersion,
                publishedVersion, ownerPreviewVersion, resolutionMode, complianceSnapshot, labels);
    }
}
