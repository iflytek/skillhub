package com.iflytek.skillhub.dto;

public record SkillLifecycleMutationResponse(
        Long skillId,
        Long versionId,
        String action,
        String status
) {}
