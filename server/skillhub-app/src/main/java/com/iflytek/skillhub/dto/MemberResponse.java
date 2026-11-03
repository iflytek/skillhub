package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;

import java.time.Instant;

public record MemberResponse(
        Long id,
        Long namespaceId,
        String userId,
        String displayName,
        String email,
        NamespaceRole role,
        Instant createdAt,
        Instant updatedAt,
        /** False when the account is disabled or unknown; such members cannot take over skills. */
        boolean active,
        boolean systemAccount
) {
    public static MemberResponse from(NamespaceMember member) {
        return new MemberResponse(
                member.getId(),
                member.getNamespaceId(),
                member.getUserId(),
                null,
                null,
                member.getRole(),
                member.getCreatedAt(),
                member.getUpdatedAt(),
                false,
                false
        );
    }

    public static MemberResponse from(NamespaceMember member, UserAccount user) {
        return new MemberResponse(
                member.getId(),
                member.getNamespaceId(),
                member.getUserId(),
                user != null ? user.getDisplayName() : null,
                user != null ? user.getEmail() : null,
                member.getRole(),
                member.getCreatedAt(),
                member.getUpdatedAt(),
                user != null && user.isActive(),
                user != null && user.isSystemAccount()
        );
    }
}
