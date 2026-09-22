package com.iflytek.skillhub.auth.connection.oidc;

import java.util.Objects;

/** Revision-pinned cache coordinate; a config switch cannot reuse old metadata. */
public record OidcMetadataCacheKey(String scopeKey, String connectionId, long revision) {

    public OidcMetadataCacheKey {
        scopeKey = requireText(scopeKey, "scopeKey");
        connectionId = requireText(connectionId, "connectionId");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
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
