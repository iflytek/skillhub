package com.iflytek.skillhub.dto;

import java.util.Map;

/**
 * Response DTO for GET /api/v1/user/profile.
 *
 * <p>Returns the current self-view profile values. When a change request is
 * pending review, private fields may reflect the latest submitted values.
 *
 * @param displayName    current self-view display name
 * @param avatarUrl      current self-view avatar URL
 * @param email          user email (read-only, not editable via profile)
 * @param pendingChanges pending change request details, or null if none
 * @param fieldPolicies  per-field edit policies (editable, requiresReview)
 */
public record UserProfileResponse(
        String displayName,
        String avatarUrl,
        String email,
        PendingChangesResponse pendingChanges,
        Map<String, FieldPolicyResponse> fieldPolicies
) {}
