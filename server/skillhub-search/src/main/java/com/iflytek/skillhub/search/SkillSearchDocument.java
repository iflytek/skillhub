package com.iflytek.skillhub.search;

public record SkillSearchDocument(
        Long skillId,
        Long namespaceId,
        String namespaceSlug,
        String ownerId,
        String title,
        String summary,
        String keywords,
        String searchText,
        String semanticVector,
        String visibility,
        String status
) {}
