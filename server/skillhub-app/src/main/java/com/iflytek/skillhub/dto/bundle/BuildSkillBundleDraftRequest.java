package com.iflytek.skillhub.dto.bundle;

import com.iflytek.skillhub.domain.bundle.SkillBundleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Builds a draft skill bundle from existing platform skills (no zip upload).
 */
public record BuildSkillBundleDraftRequest(
        @NotBlank @Size(max = 128) String slug,
        @NotBlank @Size(max = 256) String displayName,
        @NotBlank @Size(max = 32)  String version,
        @NotNull SkillBundleType type,
        @NotBlank @Size(max = 512) String summary,
        List<String> targetProjectTypes,
        List<String> roleTags,
        @NotNull List<DraftItemRequest> items,
        List<Long> mediaIds
) {
    public record DraftItemRequest(
            @NotNull Long skillId,
            @NotNull Long skillVersionId,
            @NotBlank @Size(max = 512) String roleDescription,
            boolean required,
            @Min(0) int installOrder
    ) {}
}
