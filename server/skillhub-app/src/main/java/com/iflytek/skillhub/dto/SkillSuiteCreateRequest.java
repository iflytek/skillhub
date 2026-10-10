package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request to create a Suite and its first exact-version draft. */
public record SkillSuiteCreateRequest(
        @NotBlank String namespace,
        @NotBlank String slug,
        @NotBlank @Size(max = 256) String displayName,
        @Size(max = 4000) String summary,
        @Size(max = 20000) String overview,
        @NotBlank String version,
        @NotNull SkillVisibility visibility,
        @Size(max = 4000) String changelog,
        @NotNull @Valid SkillSuiteMemberRequest entrySkill,
        @NotEmpty @Size(max = 100) List<@Valid SkillSuiteMemberRequest> members
) {
}
