package com.iflytek.skillhub.domain.security;

import java.util.Map;

public record ScanTask(
    String taskId,
    Long versionId,
    String skillPath,
    String publisherId,
    long timestamp,
    Map<String, String> options
) {}
