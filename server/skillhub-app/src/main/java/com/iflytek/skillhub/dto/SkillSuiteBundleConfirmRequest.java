package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record SkillSuiteBundleConfirmRequest(
        @NotBlank String warningDigest
) {
}
