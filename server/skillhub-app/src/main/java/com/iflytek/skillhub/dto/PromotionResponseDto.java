package com.iflytek.skillhub.dto;

import java.time.Instant;

public record PromotionResponseDto(
        Long id,
        Long sourceSkillId,
        String sourceNamespace,
        String sourceSkillSlug,
        String sourceVersion,
        String targetNamespace,
        Long targetSkillId,
        String status,
        Long submittedBy,
        String submittedByName,
        Long reviewedBy,
        String reviewedByName,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt
) {}
