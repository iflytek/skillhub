package com.iflytek.skillhub.dto;

public record PublishResultDetailResponse(
        String packagePath,
        Long skillId,
        String namespace,
        String slug,
        String version,
        String status,
        int fileCount,
        long totalSize
) {}
