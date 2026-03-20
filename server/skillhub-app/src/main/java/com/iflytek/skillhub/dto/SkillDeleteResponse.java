package com.iflytek.skillhub.dto;

public record SkillDeleteResponse(
        Long skillId,
        String namespace,
        String slug,
        boolean deleted
) {
}
