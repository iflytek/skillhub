package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record OrganizationMemberResponse(
        String id,
        String userId,
        String displayName,
        String status,
        long authorityVersion,
        Instant createdAt,
        List<OrganizationRoleBindingResponse> roles
) {
}
