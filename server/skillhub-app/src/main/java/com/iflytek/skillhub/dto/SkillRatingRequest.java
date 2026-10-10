package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record SkillRatingRequest(
        @NotNull(message = "{error.badRequest}")
        Short score
) {
}
