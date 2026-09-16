package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ReviewTaskResponse(
        Long id,
        Long skillVersionId,
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
        String subjectType,
        Long subjectId,
        Long subjectVersionId,
        String subjectSlug
) {
    /** Backward-compatible constructor for Skill-only callers and tests. */
    public ReviewTaskResponse(
            Long id,
            Long skillVersionId,
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
            Instant reviewedAt
    ) {
        this(id, skillVersionId, namespace, skillSlug, version, status,
                submittedBy, submittedByName, reviewedBy, reviewedByName,
                reviewComment, submittedAt, reviewedAt,
                "SKILL_VERSION", null, skillVersionId, skillSlug);
    }
}
