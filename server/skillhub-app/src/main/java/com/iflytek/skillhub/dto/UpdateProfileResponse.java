package com.iflytek.skillhub.dto;

/**
 * Response DTO for profile update operations.
 *
 * @param status  whether the change was applied immediately or queued for review
 * @param message human-readable status message (i18n key resolved by frontend)
 */
public record UpdateProfileResponse(
        ProfileUpdateStatus status,
        String message
) {}
