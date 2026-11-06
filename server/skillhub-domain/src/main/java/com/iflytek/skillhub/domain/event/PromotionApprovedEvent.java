package com.iflytek.skillhub.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId,
        BusinessEventSnapshot snapshot, String eventId, Instant occurredAt) implements SkillBusinessEvent {
    public PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId) {
        this(promotionId, skillId, reviewerId, submitterId, (BusinessEventSnapshot) null);
    }

    public PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, BusinessEventSnapshot snapshot) {
        this(promotionId, skillId, reviewerId, submitterId, snapshot, UUID.randomUUID().toString(),
                snapshot == null ? Instant.now() : snapshot.occurredAt());
    }

    public PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String eventId, Instant occurredAt) {
        this(promotionId, skillId, reviewerId, submitterId, null, eventId, occurredAt);
    }

    public PromotionApprovedEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(occurredAt);
    }
}
