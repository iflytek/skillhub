package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for rejecting a profile change request.
 *
 * @param comment rejection reason (required, 1-500 chars)
 */
public record ProfileReviewRejectRequest(
        @NotBlank(message = "error.profileReview.commentRequired")
        @Size(max = 500, message = "error.profileReview.commentTooLong")
        String comment
) {}
