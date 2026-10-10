package com.iflytek.skillhub.domain.event;

import com.iflytek.skillhub.domain.skill.SkillStatus;

public record SkillStatusChangedEvent(Long skillId, SkillStatus oldStatus, SkillStatus newStatus) {}
