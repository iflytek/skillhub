package com.iflytek.skillhub.domain.event;
public record ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId,
                                  String submitterId, BusinessEventSnapshot snapshot) implements SkillBusinessEvent {
    public ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId) {
        this(reviewId, skillId, versionId, reviewerId, submitterId, null);
    }
}
