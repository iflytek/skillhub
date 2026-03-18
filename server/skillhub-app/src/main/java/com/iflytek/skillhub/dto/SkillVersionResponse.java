package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SkillVersionResponse(
        Long id,
        String version,
        String status,
        String changelog,
        int fileCount,
        long totalSize,
        Instant publishedAt,
        boolean downloadAvailable
) {}
