package com.iflytek.skillhub.domain.event;

public record SkillVersionYankedEvent(Long skillId, Long versionId, String actorUserId) {}
