package com.iflytek.skillhub.domain.security;

import java.util.Map;

public record ScanTask(
        String taskId,
        Long versionId,
        String skillPath,
        String bundleKey,
        String publisherId,
        long createdAtMillis,
        Map<String, String> metadata
) {
}
