package com.iflytek.skillhub.dto;

import java.util.List;

public record PublishResponse(
        Long skillId,
        String namespace,
        String slug,
        String version,
        String status,
        int fileCount,
        long totalSize,
        List<String> labels
) {}
