package com.iflytek.skillhub.domain.organization;

import java.util.Objects;

/** Current tenant authorization coordinates used by session and credential guards. */
public record OrganizationAccessSnapshot(
        String organizationId,
        String membershipId,
        String userId,
        long organizationAuthorityVersion,
        long membershipAuthorityVersion
) {
    public OrganizationAccessSnapshot {
        organizationId = requireText(organizationId, "organizationId");
        membershipId = requireText(membershipId, "membershipId");
        userId = requireText(userId, "userId");
        if (organizationAuthorityVersion < 0 || membershipAuthorityVersion < 0) {
            throw new IllegalArgumentException("authority versions must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
