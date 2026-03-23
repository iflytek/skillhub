package com.iflytek.skillhub.domain.event;
public record ReviewRejectedEvent(Long reviewId, Long skillId, Long versionId, String reviewerId, String submitterId, String reason) {}
