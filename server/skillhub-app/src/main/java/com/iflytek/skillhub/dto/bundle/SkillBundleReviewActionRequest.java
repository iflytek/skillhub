package com.iflytek.skillhub.dto.bundle;

import jakarta.validation.constraints.Size;

public record SkillBundleReviewActionRequest(
        @Size(max = 1000) String comment
) {}
