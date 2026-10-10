package com.iflytek.skillhub.dto;

import java.time.Instant;

/** Author-facing summary for one typed resource version's review attempts. */
public record ReviewProgressResponse(
        Long latestReviewTaskId,
        Long skillId,
        String namespace,
        String skillSlug,
        String skillVersion,
        String latestStatus,
        String latestReviewComment,
        Instant latestSubmittedAt,
        Instant latestReviewedAt,
        long attemptCount,
        String subjectType,
        Long subjectId,
        Long subjectVersionId,
        String subjectSlug
) {
    /** Backward-compatible constructor for existing Skill-only callers and tests. */
    public ReviewProgressResponse(
            Long latestReviewTaskId,
            Long skillId,
            String namespace,
            String skillSlug,
            String skillVersion,
            String latestStatus,
            String latestReviewComment,
            Instant latestSubmittedAt,
            Instant latestReviewedAt,
            long attemptCount
    ) {
        this(latestReviewTaskId, skillId, namespace, skillSlug, skillVersion,
                latestStatus, latestReviewComment, latestSubmittedAt, latestReviewedAt,
                attemptCount, "SKILL_VERSION", skillId, null, skillSlug);
    }
}
