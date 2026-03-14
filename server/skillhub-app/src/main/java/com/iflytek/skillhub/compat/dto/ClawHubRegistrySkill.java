package com.iflytek.skillhub.compat.dto;

import java.util.List;
import java.util.Map;

public record ClawHubRegistrySkill(
        String slug,
        String displayName,
        String summary,
        List<String> tags,
        Map<String, Object> stats,
        long createdAt,
        long updatedAt
) {}
