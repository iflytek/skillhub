package com.iflytek.skillhub.domain.event;

import com.iflytek.skillhub.domain.review.ReviewTaskStatus;

import java.time.Instant;
import java.util.Objects;

/** Immutable result of one Suite review; delivered only after the transaction commits. */
public record SkillSuiteReviewCompletedEvent(
        Long reviewId,
        Long suiteId,
        Long versionId,
        String submitterId,
        String reviewerId,
        Long namespaceId,
        ReviewTaskStatus status,
        String eventId,
        Instant occurredAt
) {
    public SkillSuiteReviewCompletedEvent {
        if (status != ReviewTaskStatus.APPROVED && status != ReviewTaskStatus.REJECTED) {
            throw new IllegalArgumentException("Suite review result must be approved or rejected");
        }
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
