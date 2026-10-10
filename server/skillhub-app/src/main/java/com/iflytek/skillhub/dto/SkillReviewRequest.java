package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SkillReviewRequest(
        @NotNull @Min(1) @Max(5) Short score,
        @NotBlank @Size(max = 2000) String reviewText
) {}
