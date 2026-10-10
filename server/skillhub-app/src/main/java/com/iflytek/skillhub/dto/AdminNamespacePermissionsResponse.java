package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;

public record AdminNamespacePermissionsResponse(
        NamespaceRole currentUserRole,
        boolean platformOverride,
        boolean immutable,
        boolean canManageMembers,
        boolean canGovernNamespace,
        boolean canPublish,
        boolean canTransferOwnership,
        boolean canFreeze,
        boolean canUnfreeze,
        boolean canArchive,
        boolean canRestore
) {
    public static AdminNamespacePermissionsResponse forSuperAdmin(Namespace namespace,
                                                                  NamespaceRole currentUserRole,
                                                                  NamespaceAccessPolicy accessPolicy) {
        boolean mutableTeam = namespace.getType() == NamespaceType.TEAM;
        return new AdminNamespacePermissionsResponse(
                currentUserRole,
                true,
                accessPolicy.isImmutable(namespace),
                mutableTeam && namespace.getStatus() == NamespaceStatus.ACTIVE,
                mutableTeam,
                mutableTeam && namespace.getStatus() == NamespaceStatus.ACTIVE,
                mutableTeam && namespace.getStatus() == NamespaceStatus.ACTIVE,
                mutableTeam && namespace.getStatus() == NamespaceStatus.ACTIVE,
                mutableTeam && namespace.getStatus() == NamespaceStatus.FROZEN,
                mutableTeam && namespace.getStatus() != NamespaceStatus.ARCHIVED,
                mutableTeam && namespace.getStatus() == NamespaceStatus.ARCHIVED
        );
    }
}
