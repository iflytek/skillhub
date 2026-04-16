package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to submit a skill version for review.
 */
public record SubmitReviewRequest(
    @NotBlank(message = "Version is required")
    String version,

    @NotBlank(message = "Target visibility is required")
    @Pattern(regexp = "PUBLIC|NAMESPACE_ONLY", message = "Target visibility must be PUBLIC or NAMESPACE_ONLY")
    String targetVisibility
) {}
