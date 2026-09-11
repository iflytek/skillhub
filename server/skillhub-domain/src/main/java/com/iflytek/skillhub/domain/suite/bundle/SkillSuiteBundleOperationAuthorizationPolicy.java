package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;

import java.util.Map;
import java.util.Set;

/** Authorization shared by Bundle operation status and control commands. */
public final class SkillSuiteBundleOperationAuthorizationPolicy {

    private SkillSuiteBundleOperationAuthorizationPolicy() {
    }

    public static boolean canAccess(
            SkillSuiteBundleExecutionOperation operation,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        if (operation.getActorId().equals(actorId)) {
            return true;
        }
        if (platformRoles != null && platformRoles.contains("SUPER_ADMIN")) {
            return true;
        }
        NamespaceRole role = namespaceRoles == null ? null : namespaceRoles.get(operation.getNamespaceId());
        return role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN;
    }
}
