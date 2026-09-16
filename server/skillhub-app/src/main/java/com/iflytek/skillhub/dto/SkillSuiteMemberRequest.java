package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Exact Skill coordinate selected for a Suite draft. */
public record SkillSuiteMemberRequest(
        @NotNull Long skillVersionId,
        @NotBlank String namespace,
        @NotBlank String slug,
        @NotBlank String version
) {
}
