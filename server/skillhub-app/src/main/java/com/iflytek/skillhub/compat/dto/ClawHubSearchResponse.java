package com.iflytek.skillhub.compat.dto;

import java.util.List;

public record ClawHubSearchResponse(
    List<ClawHubSearchResult> results
) {
    public record ClawHubSearchResult(
        String slug,
        String displayName,
        String summary,
        String version,
        double score,
        Long updatedAt
    ) {}
}
