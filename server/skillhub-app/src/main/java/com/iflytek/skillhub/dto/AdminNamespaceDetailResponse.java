package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.Namespace;
import java.time.Instant;

public record AdminNamespaceDetailResponse(
        Long id,
        String slug,
        String displayName,
        String status,
        String description,
        String type,
        String avatarUrl,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        AdminNamespaceStatsResponse stats,
        AdminNamespacePermissionsResponse permissions
) {
    public static AdminNamespaceDetailResponse from(Namespace namespace,
                                                    AdminNamespaceStatsResponse stats,
                                                    AdminNamespacePermissionsResponse permissions) {
        return new AdminNamespaceDetailResponse(
                namespace.getId(),
                namespace.getSlug(),
                namespace.getDisplayName(),
                namespace.getStatus().name(),
                namespace.getDescription(),
                namespace.getType().name(),
                namespace.getAvatarUrl(),
                namespace.getCreatedBy(),
                namespace.getCreatedAt(),
                namespace.getUpdatedAt(),
                stats,
                permissions
        );
    }
}
