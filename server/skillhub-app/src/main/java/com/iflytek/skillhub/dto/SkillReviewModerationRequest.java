package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Size;

public record SkillReviewModerationRequest(
        @Size(max = 500) String reason
) {}
