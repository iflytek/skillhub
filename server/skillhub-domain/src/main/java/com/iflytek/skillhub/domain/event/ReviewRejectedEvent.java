package com.iflytek.skillhub.domain.event;
public record ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId,
                                  String submitterId, String reason, BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, reason, null);
    }
}
