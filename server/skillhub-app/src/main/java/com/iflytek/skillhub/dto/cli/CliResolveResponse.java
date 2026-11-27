package com.iflytek.skillhub.dto.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iflytek.skillhub.dto.GithubSkillSourceResponse;
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
        String downloadUrl,
        // Omitted for versions that did not come from GitHub sync; older CLIs ignore it.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        GithubSkillSourceResponse githubSource
) {
    public CliResolveResponse(String namespace, String slug, String version, Long versionId,
                              String fingerprint, String downloadUrl) {
        this(namespace, slug, version, versionId, fingerprint, downloadUrl, null);
    }
}
