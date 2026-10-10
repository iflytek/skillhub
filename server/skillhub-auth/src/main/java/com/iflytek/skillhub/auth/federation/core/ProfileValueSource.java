package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Stable source identity recorded with a single profile field value. */
public record ProfileValueSource(ProfileSourceKind kind, String sourceId, ProfileScope scope) {

    public ProfileValueSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceId, "sourceId");
        sourceId = sourceId.trim();
        if (sourceId.isEmpty()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        Objects.requireNonNull(scope, "scope");
        if (requiresOrganization(kind) && !(scope instanceof ProfileScope.Organization)) {
            throw new IllegalArgumentException(kind + " source requires organization scope");
        }
    }

    private static boolean requiresOrganization(ProfileSourceKind kind) {
        return kind == ProfileSourceKind.ORGANIZATION_ADMIN;
    }
}
