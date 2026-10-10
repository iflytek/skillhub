package com.iflytek.skillhub.domain.event;
public record ReviewSubmittedEvent(Long reviewId, Long skillId, Long versionId, String submitterId, Long namespaceId) {}
