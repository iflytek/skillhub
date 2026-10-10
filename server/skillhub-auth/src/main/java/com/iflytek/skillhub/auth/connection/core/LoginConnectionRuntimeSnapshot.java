package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;
import java.util.Optional;

/** Immutable connection revision consumed by the authentication data plane. */
public record LoginConnectionRuntimeSnapshot<C extends LoginConnectionRuntimeConfig>(
        Optional<String> organizationId,
        String connectionId,
        ConnectionHandle handle,
        long revision,
        AdapterKey adapterKey,
        int adapterContractVersion,
        int configSchemaVersion,
        InteractionModel interactionModel,
        C config
) {

    public LoginConnectionRuntimeSnapshot {
        organizationId = normalizeOptionalText(organizationId, "organizationId");
        connectionId = requireText(connectionId, "connectionId");
        Objects.requireNonNull(handle, "connection handle must not be null");
        if (revision < 1) {
            throw new IllegalArgumentException("connection revision must be positive");
        }
        Objects.requireNonNull(adapterKey, "adapter key must not be null");
        if (adapterContractVersion < 1) {
            throw new IllegalArgumentException("adapter contract version must be positive");
        }
        if (configSchemaVersion < 1) {
            throw new IllegalArgumentException("config schema version must be positive");
        }
        Objects.requireNonNull(interactionModel, "interaction model must not be null");
        Objects.requireNonNull(config, "runtime config must not be null");
    }

    private static Optional<String> normalizeOptionalText(Optional<String> value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        return value.map(text -> requireText(text, field));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
