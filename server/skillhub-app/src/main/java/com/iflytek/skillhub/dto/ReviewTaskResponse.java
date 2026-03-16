package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ReviewTaskResponse(
        Long id,
        Long skillVersionId,
        Long skillId,
        String namespace,
        String skillSlug,
        String version,
        String status,
        String submittedBy,
        String submittedByName,
        String reviewedBy,
        String reviewedByName,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt,
        boolean hasSecurityAudit
) {}
