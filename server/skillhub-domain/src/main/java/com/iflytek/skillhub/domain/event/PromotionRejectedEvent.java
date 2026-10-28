package com.iflytek.skillhub.domain.event;
public record PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId,
                                     String submitterId, String reason, BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public PromotionRejectedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId, String reason) {
        this(promotionId, skillId, reviewerId, submitterId, reason, null);
    }
}
