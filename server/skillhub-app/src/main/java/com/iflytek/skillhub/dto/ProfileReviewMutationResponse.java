package com.iflytek.skillhub.dto;

/**
 * Response DTO for profile review approve/reject mutations.
 *
 * @param id     change request ID
 * @param status resulting status after the action (APPROVED or REJECTED)
 */
public record ProfileReviewMutationResponse(
        Long id,
        String status
) {}
