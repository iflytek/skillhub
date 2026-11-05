package com.iflytek.skillhub.domain.event;
public record PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId,
                                     String submitterId, BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public PromotionApprovedEvent(Long promotionId, Long skillId, String reviewerId, String submitterId) {
        this(promotionId, skillId, reviewerId, submitterId, null);
    }
}
