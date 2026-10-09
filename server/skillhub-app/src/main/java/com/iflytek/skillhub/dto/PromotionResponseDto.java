package com.iflytek.skillhub.dto;

import java.time.Instant;

public record PromotionResponseDto(
        Long id,
        Long sourceSkillId,
        String sourceSkillDisplayName,
        String sourceSkillSummary,
        String sourceNamespace,
        String sourceSkillSlug,
        String sourceVersion,
        Integer sourceVersionFileCount,
        Long sourceVersionTotalSize,
        Long sourceSkillDownloadCount,
        Integer sourceSkillStarCount,
        String targetNamespace,
        Long targetSkillId,
        String status,
        String submittedBy,
        String submittedByName,
        String reviewedBy,
        String reviewedByName,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt,
        String requestKind,
        String targetCurrentVersion,
        Long targetVersionId
) {
    public PromotionResponseDto(Long id, Long sourceSkillId, String sourceSkillDisplayName,
                                String sourceSkillSummary, String sourceNamespace, String sourceSkillSlug,
                                String sourceVersion, Integer sourceVersionFileCount, Long sourceVersionTotalSize,
                                Long sourceSkillDownloadCount, Integer sourceSkillStarCount,
                                String targetNamespace, Long targetSkillId, String status, String submittedBy,
                                String submittedByName, String reviewedBy, String reviewedByName,
                                String reviewComment, Instant submittedAt, Instant reviewedAt) {
        this(id, sourceSkillId, sourceSkillDisplayName, sourceSkillSummary, sourceNamespace,
                sourceSkillSlug, sourceVersion, sourceVersionFileCount, sourceVersionTotalSize,
                sourceSkillDownloadCount, sourceSkillStarCount, targetNamespace, targetSkillId,
                status, submittedBy, submittedByName, reviewedBy, reviewedByName,
                reviewComment, submittedAt, reviewedAt, "INITIAL", null, null);
    }
}
