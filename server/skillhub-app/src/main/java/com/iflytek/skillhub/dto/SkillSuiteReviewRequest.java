package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Size;

/** Optional reviewer explanation for a Suite review decision. */
public record SkillSuiteReviewRequest(@Size(max = 2000) String comment) {
}
