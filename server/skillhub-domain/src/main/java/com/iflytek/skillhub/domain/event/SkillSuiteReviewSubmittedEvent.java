package com.iflytek.skillhub.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A Suite version has entered review; consumers run only after its transaction commits. */
public record SkillSuiteReviewSubmittedEvent(
        Long reviewId,
        Long suiteId,
        Long versionId,
        String submitterId,
        Long namespaceId,
        String eventId,
        Instant occurredAt
) {
    public SkillSuiteReviewSubmittedEvent(Long reviewId, Long suiteId, Long versionId,
            String submitterId, Long namespaceId) {
        this(reviewId, suiteId, versionId, submitterId, namespaceId,
                UUID.randomUUID().toString(), Instant.now());
    }

    public SkillSuiteReviewSubmittedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
    }
}
