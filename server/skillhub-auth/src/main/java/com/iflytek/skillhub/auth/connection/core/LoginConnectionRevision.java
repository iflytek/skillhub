package com.iflytek.skillhub.auth.connection.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable, validated control-plane revision eligible for explicit activation. */
public record LoginConnectionRevision<C extends LoginConnectionRuntimeConfig>(
        Optional<String> organizationId,
        String connectionId,
        ConnectionHandle handle,
        long revision,
        AdapterDescriptor descriptor,
        C config,
        Instant createdAt
) {

    public LoginConnectionRevision {
        organizationId = normalizeOptionalText(organizationId, "organizationId");
        connectionId = requireText(connectionId, "connectionId");
        Objects.requireNonNull(handle, "handle");
        if (revision < 1) {
            throw new IllegalArgumentException("connection revision must be positive");
        }
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.connectionKind() != ConnectionKind.LOGIN) {
            throw new IllegalArgumentException("login revision requires a login adapter descriptor");
        }
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    LoginConnectionRuntimeSnapshot<C> toRuntimeSnapshot() {
        return new LoginConnectionRuntimeSnapshot<>(
                organizationId,
                connectionId,
                handle,
                revision,
                descriptor,
                config
        );
    }

    private static Optional<String> normalizeOptionalText(Optional<String> value, String field) {
        Objects.requireNonNull(value, field);
        return value.map(text -> requireText(text, field));
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
