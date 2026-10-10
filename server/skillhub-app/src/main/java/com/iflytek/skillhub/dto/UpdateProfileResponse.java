package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Response DTO for profile update operations.
 *
 * @param status        whether the change was applied immediately, queued, or mixed
 * @param message       human-readable status message (i18n key resolved by frontend)
 * @param appliedFields fields that were applied immediately (null when not mixed)
 * @param pendingFields fields that are queued for review (null when not mixed)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProfileResponse(
        ProfileUpdateStatus status,
        String message,
        Map<String, String> appliedFields,
        Map<String, String> pendingFields
) {
    public UpdateProfileResponse(ProfileUpdateStatus status, String message) {
        this(status, message, null, null);
    }
}
