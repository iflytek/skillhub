package com.iflytek.skillhub.dto.cli;

import io.swagger.v3.oas.annotations.media.Schema;

public record CliResolveResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String namespace,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String slug,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long versionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String fingerprint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String downloadUrl
) {}
