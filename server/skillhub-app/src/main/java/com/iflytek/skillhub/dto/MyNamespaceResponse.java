package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;

import java.time.LocalDateTime;

public record MyNamespaceResponse(
        Long id,
        String slug,
        String displayName,
        NamespaceStatus status,
        String description,
        NamespaceType type,
        String avatarUrl,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        NamespaceRole currentUserRole,
        boolean immutable,
        boolean canFreeze,
        boolean canUnfreeze,
        boolean canArchive,
        boolean canRestore
) {
    public static MyNamespaceResponse from(Namespace namespace,
                                           NamespaceRole currentUserRole,
                                           NamespaceAccessPolicy accessPolicy) {
        return new MyNamespaceResponse(
                namespace.getId(),
                namespace.getSlug(),
                namespace.getDisplayName(),
                namespace.getStatus(),
                namespace.getDescription(),
                namespace.getType(),
                namespace.getAvatarUrl(),
                namespace.getCreatedBy(),
                namespace.getCreatedAt(),
                namespace.getUpdatedAt(),
                currentUserRole,
                accessPolicy.isImmutable(namespace),
                accessPolicy.canFreeze(namespace, currentUserRole),
                accessPolicy.canUnfreeze(namespace, currentUserRole),
                accessPolicy.canArchive(namespace, currentUserRole),
                accessPolicy.canRestore(namespace, currentUserRole)
        );
    }
}
