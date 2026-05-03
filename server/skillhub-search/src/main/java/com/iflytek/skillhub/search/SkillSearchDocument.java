package com.iflytek.skillhub.search;

import java.util.List;

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
        String status,
        List<String> labelSlugs,
        long downloadCount,
        double ratingAvg,
        long updatedAtEpochMillis,
        String namespaceStatus,
        boolean hidden
) {
    public SkillSearchDocument(
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
            String status) {
        this(
                skillId,
                namespaceId,
                namespaceSlug,
                ownerId,
                title,
                summary,
                keywords,
                searchText,
                semanticVector,
                visibility,
                status,
                List.of(),
                0L,
                0D,
                0L,
                "ACTIVE",
                false
        );
    }
}
