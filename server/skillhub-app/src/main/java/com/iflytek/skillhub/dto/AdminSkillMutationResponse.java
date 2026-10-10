package com.iflytek.skillhub.dto;

public record AdminSkillMutationResponse(
    Long skillId,
    Long versionId,
    String action,
    String status
) {}
