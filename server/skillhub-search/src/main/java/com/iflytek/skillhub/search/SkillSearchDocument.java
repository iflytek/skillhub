package com.iflytek.skillhub.search;

/**
 * Denormalized search document model written to and read from the search subsystem.
 */
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
