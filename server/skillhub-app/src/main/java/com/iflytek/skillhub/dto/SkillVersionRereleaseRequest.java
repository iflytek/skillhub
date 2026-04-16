package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record SkillVersionRereleaseRequest(
        @NotBlank(message = "{validation.required}")
        String targetVersion,
        boolean confirmWarnings
) {
}
