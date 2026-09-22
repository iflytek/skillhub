package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;
import java.util.Optional;

/** Immutable control-plane view of editable, not-yet-validated adapter configuration. */
public record LoginConnectionDraft<D>(
        Optional<String> organizationId,
        String connectionId,
        ConnectionHandle handle,
        AdapterKey adapterKey,
        int configSchemaVersion,
        D editableConfig
) {

    public LoginConnectionDraft {
        organizationId = normalizeOptionalText(organizationId, "organizationId");
        connectionId = requireText(connectionId, "connectionId");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(adapterKey, "adapterKey");
        if (configSchemaVersion < 1) {
            throw new IllegalArgumentException("config schema version must be positive");
        }
        Objects.requireNonNull(editableConfig, "editableConfig");
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
