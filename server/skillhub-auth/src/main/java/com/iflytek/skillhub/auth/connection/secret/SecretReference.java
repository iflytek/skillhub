package com.iflytek.skillhub.auth.connection.secret;

import java.util.Objects;

/** Non-secret, version-pinned reference stored by an immutable connection revision. */
public record SecretReference(
        String scopeKey,
        String connectionId,
        SecretPurpose purpose,
        long bindingVersion
) {

    public static final String PLATFORM_SCOPE_KEY = "@platform";

    public SecretReference {
        scopeKey = requireText(scopeKey, "scopeKey");
        connectionId = requireText(connectionId, "connectionId");
        purpose = Objects.requireNonNull(purpose, "purpose");
        if (bindingVersion < 1) {
            throw new IllegalArgumentException("bindingVersion must be positive");
        }
    }

    SecretBindingContext context() {
        return new SecretBindingContext(scopeKey, connectionId, purpose, bindingVersion);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
