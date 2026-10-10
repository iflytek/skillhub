package com.iflytek.skillhub.dto.cli;

import java.time.Instant;

/**
 * Installable skill metadata used by the CLI namespace workspace synchronizer.
 */
public record CliNamespaceSyncItemResponse(
        String namespace,
        String slug,
        String version,
        Long versionId,
        String fingerprint,
        Instant updatedAt,
        String visibility,
        String downloadUrl
) {}
