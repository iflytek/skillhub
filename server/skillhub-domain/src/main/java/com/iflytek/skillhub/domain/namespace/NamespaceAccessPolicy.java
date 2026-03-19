package com.iflytek.skillhub.domain.namespace;

import org.springframework.stereotype.Component;

/**
 * Encapsulates namespace lifecycle rules that determine which management actions are currently
 * allowed.
 */
@Component
public class NamespaceAccessPolicy {

    public boolean isImmutable(Namespace namespace) {
        return namespace.getType() == NamespaceType.GLOBAL;
    }

    public boolean canMutateSettings(Namespace namespace) {
        return namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() == NamespaceStatus.ACTIVE;
    }

    public boolean canManageMembers(Namespace namespace) {
        return canMutateSettings(namespace);
    }

    public boolean canTransferOwnership(Namespace namespace) {
        return canMutateSettings(namespace);
    }

    public boolean canFreeze(Namespace namespace, NamespaceRole role) {
        return namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() == NamespaceStatus.ACTIVE
                && (role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN);
    }

    public boolean canUnfreeze(Namespace namespace, NamespaceRole role) {
        return namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() == NamespaceStatus.FROZEN
                && (role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN);
    }

    public boolean canArchive(Namespace namespace, NamespaceRole role) {
        return namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() != NamespaceStatus.ARCHIVED
                && role == NamespaceRole.OWNER;
    }

    public boolean canRestore(Namespace namespace, NamespaceRole role) {
        return namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() == NamespaceStatus.ARCHIVED
                && role == NamespaceRole.OWNER;
    }
}
