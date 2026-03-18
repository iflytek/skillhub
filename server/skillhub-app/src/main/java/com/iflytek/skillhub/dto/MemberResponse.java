package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;

import java.time.Instant;

public record MemberResponse(
        Long id,
        Long namespaceId,
        String userId,
        NamespaceRole role,
        Instant createdAt,
        Instant updatedAt
) {
    public static MemberResponse from(NamespaceMember member) {
        return new MemberResponse(
                member.getId(),
                member.getNamespaceId(),
                member.getUserId(),
                member.getRole(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
