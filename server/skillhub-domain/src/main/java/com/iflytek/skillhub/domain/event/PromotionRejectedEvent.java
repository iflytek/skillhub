package com.iflytek.skillhub.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason,
        BusinessEventSnapshot snapshot, String eventId, Instant occurredAt) implements SkillBusinessEvent {
    public PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason) {
        this(promotionId, skillId, reviewerId, submitterId, reason, (BusinessEventSnapshot) null);
    }

    public PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason, BusinessEventSnapshot snapshot) {
        this(promotionId, skillId, reviewerId, submitterId, reason, snapshot, UUID.randomUUID().toString(),
                snapshot == null ? Instant.now() : snapshot.occurredAt());
    }

    public PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason, String eventId, Instant occurredAt) {
        this(promotionId, skillId, reviewerId, submitterId, reason, null, eventId, occurredAt);
    }

    public PromotionRejectedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
    }
}
