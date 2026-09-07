package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;
import java.util.Optional;

/** Immutable connection revision consumed by the authentication data plane. */
public record LoginConnectionRuntimeSnapshot<C extends LoginConnectionRuntimeConfig>(
        Optional<String> organizationId,
        String connectionId,
        ConnectionHandle handle,
        long revision,
        AdapterDescriptor descriptor,
        C config
) {

    public LoginConnectionRuntimeSnapshot {
        organizationId = normalizeOptionalText(organizationId, "organizationId");
        connectionId = requireText(connectionId, "connectionId");
        Objects.requireNonNull(handle, "connection handle must not be null");
        if (revision < 1) {
            throw new IllegalArgumentException("connection revision must be positive");
        }
        Objects.requireNonNull(descriptor, "adapter descriptor must not be null");
        if (descriptor.connectionKind() != ConnectionKind.LOGIN) {
            throw new IllegalArgumentException("login runtime snapshot requires a login adapter descriptor");
        }
        Objects.requireNonNull(config, "runtime config must not be null");
    }

    public AdapterKey adapterKey() {
        return descriptor.adapterKey();
    }

    public AdapterContractVersion adapterContractVersion() {
        return descriptor.contractVersion();
    }

    public int configSchemaVersion() {
        return descriptor.configSchemaVersion();
    }

    public InteractionModel interactionModel() {
        return descriptor.interactionModel().orElseThrow();
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
