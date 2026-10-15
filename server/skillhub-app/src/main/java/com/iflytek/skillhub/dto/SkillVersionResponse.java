package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record SkillVersionResponse(
        Long id,
        String version,
        String status,
        @Schema(nullable = true) String changelog,
        int fileCount,
        long totalSize,
        @Schema(nullable = true) Instant publishedAt,
        boolean downloadAvailable,
        ComplianceSnapshotResponse complianceSnapshot
) {}
