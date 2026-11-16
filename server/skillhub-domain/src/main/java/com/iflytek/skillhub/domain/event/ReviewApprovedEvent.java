package com.iflytek.skillhub.domain.event;

import com.iflytek.skillhub.domain.review.ReviewDecisionMethod;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A review task was approved. {@code reviewerId} is {@code null} for a
 * {@link ReviewDecisionMethod#SYSTEM_AUTOMATIC} decision; the submitter is never reported as reviewer.
 */
public record ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId,
        BusinessEventSnapshot snapshot, String eventId, Instant occurredAt,
        ReviewDecisionMethod decisionMethod) implements SkillBusinessEvent {
    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, (BusinessEventSnapshot) null);
    }

    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId,
            BusinessEventSnapshot snapshot) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, snapshot, ReviewDecisionMethod.HUMAN);
    }

    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId,
            BusinessEventSnapshot snapshot, ReviewDecisionMethod decisionMethod) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, snapshot, UUID.randomUUID().toString(),
                snapshot == null ? Instant.now() : snapshot.occurredAt(), decisionMethod);
    }

    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId,
            String eventId, Instant occurredAt) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, null, eventId, occurredAt,
                ReviewDecisionMethod.HUMAN);
    }

    public ReviewApprovedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(decisionMethod);
    }
}
