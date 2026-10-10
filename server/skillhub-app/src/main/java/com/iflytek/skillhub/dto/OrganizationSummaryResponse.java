package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.Set;

public record OrganizationSummaryResponse(
        String id,
        String slug,
        String displayName,
        String status,
        long authorityVersion,
        String membershipId,
        long membershipAuthorityVersion,
        Set<String> roles,
        Instant memberSince,
        Instant updatedAt,
        OrganizationPermissionsResponse permissions
) {
}
