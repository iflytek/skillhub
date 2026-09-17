package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Separates platform-global profile data from organization-local profile data. */
public sealed interface ProfileScope permits ProfileScope.Global, ProfileScope.Organization {

    static ProfileScope global() {
        return new Global();
    }

    static ProfileScope organization(String organizationId) {
        return new Organization(organizationId);
    }

    record Global() implements ProfileScope {
    }

    record Organization(String organizationId) implements ProfileScope {
        public Organization {
            Objects.requireNonNull(organizationId, "organizationId");
            organizationId = organizationId.trim();
            if (organizationId.isEmpty()) {
                throw new IllegalArgumentException("organizationId must not be blank");
            }
        }
    }
}
