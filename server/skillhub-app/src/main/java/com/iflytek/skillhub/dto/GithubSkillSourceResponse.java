package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Public provenance for a published GitHub-synced version. */
public record GithubSkillSourceResponse(
        String ownerLogin,
        String repositoryName,
        String skillPath,
        String commitSha,
        int syncRevision,
        String licenseStatus,
        List<LicenseDocument> licenseDocuments,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UpstreamTag upstreamTag,
        @Schema(nullable = true, description = "Version declared in this Skill's SKILL.md, independent of repository tags")
        String declaredVersion
) {
    public record LicenseDocument(String scope, String path, boolean bundled) {}

    /**
     * Repository-level semver tag relation captured at acquisition time.
     * {@code scope} is always REPOSITORY: Skills from one repository share it.
     */
    public record UpstreamTag(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"EXACT", "AFTER_TAG", "NO_TAG", "UNKNOWN", "UNRECORDED"})
            String status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"REPOSITORY"})
            String scope,
            @Schema(nullable = true) String tag,
            @Schema(nullable = true) String tagCommitSha,
            @Schema(nullable = true) Integer commitsAfterTag,
            @Schema(nullable = true) String reason
    ) {
        public static final String REPOSITORY = "REPOSITORY";
        public static final String UNRECORDED = "UNRECORDED";

        public static UpstreamTag unrecorded() {
            return new UpstreamTag(UNRECORDED, REPOSITORY, null, null, null, null);
        }
    }
}
