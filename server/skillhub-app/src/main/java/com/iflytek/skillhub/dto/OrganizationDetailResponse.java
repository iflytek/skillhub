package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.Set;

public record OrganizationDetailResponse(
        String id,
        String slug,
        String displayName,
        String status,
        long authorityVersion,
        String membershipId,
        long membershipAuthorityVersion,
        Set<String> roles,
        Instant memberSince,
        Instant createdAt,
        Instant updatedAt,
        OrganizationPermissionsResponse permissions
) {
}
