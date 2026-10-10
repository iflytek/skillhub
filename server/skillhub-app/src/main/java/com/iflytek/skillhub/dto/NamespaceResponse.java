package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;

import java.time.Instant;

public record NamespaceResponse(
        Long id,
        String slug,
        String displayName,
        NamespaceStatus status,
        String description,
        NamespaceType type,
        String avatarUrl,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static NamespaceResponse from(Namespace namespace) {
        return new NamespaceResponse(
                namespace.getId(),
                namespace.getSlug(),
                namespace.getDisplayName(),
                namespace.getStatus(),
                namespace.getDescription(),
                namespace.getType(),
                namespace.getAvatarUrl(),
                namespace.getCreatedBy(),
                namespace.getCreatedAt(),
                namespace.getUpdatedAt()
        );
    }
}
