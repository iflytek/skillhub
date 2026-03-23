package com.iflytek.skillhub.domain.event;
public record ReviewApprovedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId) {}
