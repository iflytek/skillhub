package com.iflytek.skillhub.dto;

public record PublishResponse(
        Long skillId,
        String namespace,
        String slug,
        String version,
        String status,
        int fileCount,
        long totalSize
) {}
