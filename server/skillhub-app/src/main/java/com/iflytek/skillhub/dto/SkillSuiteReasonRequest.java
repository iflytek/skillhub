package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Required operator reason for destructive Suite lifecycle actions. */
public record SkillSuiteReasonRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
