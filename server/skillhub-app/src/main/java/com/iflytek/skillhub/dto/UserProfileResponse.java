package com.iflytek.skillhub.dto;

/**
 * Response DTO for GET /api/v1/user/profile.
 *
 * <p>Returns the current (approved) profile values plus any pending
 * change request awaiting review.
 *
 * @param displayName    current approved display name
 * @param avatarUrl      current approved avatar URL
 * @param email          user email (read-only, not editable via profile)
 * @param pendingChanges pending change request details, or null if none
 */
public record UserProfileResponse(
        String displayName,
        String avatarUrl,
        String email,
        PendingChangesResponse pendingChanges
) {}
