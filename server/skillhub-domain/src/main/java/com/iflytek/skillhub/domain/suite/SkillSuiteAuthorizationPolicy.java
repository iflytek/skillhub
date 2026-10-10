package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;

/** Shared caller policy used by both Suite commands and action discovery. */
public final class SkillSuiteAuthorizationPolicy {

    private SkillSuiteAuthorizationPolicy() {
    }

    public static boolean canManageVersion(
            SkillSuite suite,
            SkillSuiteVersion version,
            SkillSuiteActionContext context
    ) {
        NamespaceRole role = context.namespaceRoles().get(suite.getNamespaceId());
        return isPlatformOrNamespaceAdmin(role, context)
                || (context.actorUserId() != null
                && role != null
                && context.actorUserId().equals(suite.getCreatedBy()));
    }

    public static boolean canCreateVersion(SkillSuite suite, SkillSuiteActionContext context) {
        NamespaceRole role = context.namespaceRoles().get(suite.getNamespaceId());
        return isPlatformOrNamespaceAdmin(role, context)
                || (context.actorUserId() != null
                && role != null
                && context.actorUserId().equals(suite.getCreatedBy()));
    }

    public static boolean canAdminister(SkillSuite suite, SkillSuiteActionContext context) {
        return isPlatformOrNamespaceAdmin(context.namespaceRoles().get(suite.getNamespaceId()), context);
    }

    private static boolean isPlatformOrNamespaceAdmin(
            NamespaceRole role,
            SkillSuiteActionContext context
    ) {
        return context.platformRoles().contains("SUPER_ADMIN")
                || role == NamespaceRole.OWNER
                || role == NamespaceRole.ADMIN;
    }
}
