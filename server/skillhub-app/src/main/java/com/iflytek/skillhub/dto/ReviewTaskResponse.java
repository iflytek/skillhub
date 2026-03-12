package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ReviewTaskResponse(
        Long id,
        Long skillVersionId,
        String namespace,
        String skillSlug,
        String version,
        String status,
        Long submittedBy,
        String submittedByName,
        Long reviewedBy,
        String reviewedByName,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt
) {}
