package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import jakarta.validation.constraints.NotNull;

public record MemberRequest(
        @NotNull(message = "{validation.member.userId.notNull}")
        String userId,

        @NotNull(message = "{validation.member.role.notNull}")
        NamespaceRole role
) {}
