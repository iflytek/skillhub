package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ExternalRoleGrantRuleResponse(
        long id,
        String providerCode,
        String email,
        String roleCode,
        String status,
        String matchedSubject,
        String grantedUserId,
        Instant grantedAt,
        long version,
        Instant updatedAt
) {}
