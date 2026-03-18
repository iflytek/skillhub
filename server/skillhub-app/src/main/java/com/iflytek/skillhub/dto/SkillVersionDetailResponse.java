package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SkillVersionDetailResponse(
        Long id,
        String version,
        String status,
        String changelog,
        int fileCount,
        long totalSize,
        Instant publishedAt,
        String parsedMetadataJson,
        String manifestJson
) {}
