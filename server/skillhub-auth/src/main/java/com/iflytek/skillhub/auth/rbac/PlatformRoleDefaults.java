package com.iflytek.skillhub.auth.rbac;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utility methods for normalizing platform role sets and ensuring a baseline user role.
 */
public final class PlatformRoleDefaults {

    public static final String DEFAULT_USER_ROLE = "USER";

    private PlatformRoleDefaults() {
    }

    public static Set<String> withDefaultUserRole(Collection<String> roles) {
        Set<String> resolvedRoles = new TreeSet<>();
        if (roles != null) {
            resolvedRoles.addAll(roles);
        }
        if (resolvedRoles.isEmpty()) {
            resolvedRoles.add(DEFAULT_USER_ROLE);
        }
        return Set.copyOf(resolvedRoles);
    }
}
