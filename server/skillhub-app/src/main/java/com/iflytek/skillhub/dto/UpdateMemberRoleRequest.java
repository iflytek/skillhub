package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "{validation.member.role.notNull}")
        NamespaceRole role
) {}
