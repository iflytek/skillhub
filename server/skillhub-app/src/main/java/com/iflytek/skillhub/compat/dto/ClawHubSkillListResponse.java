package com.iflytek.skillhub.compat.dto;

import java.util.List;

public record ClawHubSkillListResponse(
    List<SkillListItem> items,
    String nextCursor
) {
    public record SkillListItem(
        String slug,
        String displayName,
        String summary,
        Object tags,
        Object stats,
        long createdAt,
        long updatedAt,
        LatestVersion latestVersion
    ) {
        public record LatestVersion(
            String version,
            long createdAt,
            String changelog,
            String license
        ) {}
    }
}
