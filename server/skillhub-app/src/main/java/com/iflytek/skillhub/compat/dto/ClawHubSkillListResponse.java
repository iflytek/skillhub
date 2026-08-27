package com.iflytek.skillhub.compat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iflytek.skillhub.dto.SkillLabelDto;
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
        LatestVersion latestVersion,
        /**
         * Labels attached to the skill, present only when the caller passes
         * {@code include=labels}. Omitted otherwise, so the legacy payload is unchanged.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<SkillLabelDto> labels
    ) {

        public SkillListItem(
            String slug,
            String displayName,
            String summary,
            Object tags,
            Object stats,
            long createdAt,
            long updatedAt,
            LatestVersion latestVersion) {
            this(slug, displayName, summary, tags, stats, createdAt, updatedAt, latestVersion, null);
        }

        public record LatestVersion(
            String version,
            long createdAt,
            String changelog,
            String license
        ) {}
    }
}
