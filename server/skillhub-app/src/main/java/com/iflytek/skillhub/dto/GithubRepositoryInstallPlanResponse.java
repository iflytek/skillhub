package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GithubRepositoryInstallPlanResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String repositoryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String owner,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Member> members
) {
    public record Member(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long skillId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String namespace,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long versionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fingerprint,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String downloadUrl
    ) {}
}
