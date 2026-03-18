package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillTag;

import java.time.Instant;

public record TagResponse(
        Long id,
        String tagName,
        Long versionId,
        Instant createdAt
) {
    public static TagResponse from(SkillTag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getTagName(),
                tag.getVersionId(),
                tag.getCreatedAt()
        );
    }
}
