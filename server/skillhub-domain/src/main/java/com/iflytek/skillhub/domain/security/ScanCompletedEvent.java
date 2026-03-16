package com.iflytek.skillhub.domain.security;

public record ScanCompletedEvent(
    Long versionId,
    SecurityVerdict verdict,
    int findingsCount
) {}
