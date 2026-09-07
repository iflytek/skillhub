package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import java.util.Objects;
import java.util.Optional;

/** Immutable non-secret revision view handed to testing and authentication materializers. */
public record LoginConnectionRevisionSnapshot(
        Optional<String> organizationId,
        String connectionId,
        ConnectionHandle publicHandle,
        String revisionId,
        long revision,
        AdapterKey adapterKey,
        String adapterContractVersion,
        int configSchemaVersion,
        String capabilitiesJson,
        String typedConfigJson,
        Optional<Long> secretBindingVersion
) {

    public LoginConnectionRevisionSnapshot {
        organizationId = Objects.requireNonNull(organizationId, "organizationId")
                .map(value -> requireText(value, "organizationId"));
        connectionId = requireText(connectionId, "connectionId");
        Objects.requireNonNull(publicHandle, "publicHandle");
        revisionId = requireText(revisionId, "revisionId");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        Objects.requireNonNull(adapterKey, "adapterKey");
        adapterContractVersion = requireText(adapterContractVersion, "adapterContractVersion");
        if (configSchemaVersion < 1) {
            throw new IllegalArgumentException("configSchemaVersion must be positive");
        }
        capabilitiesJson = requireText(capabilitiesJson, "capabilitiesJson");
        typedConfigJson = requireText(typedConfigJson, "typedConfigJson");
        secretBindingVersion = Objects.requireNonNull(secretBindingVersion, "secretBindingVersion");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "LoginConnectionRevisionSnapshot[organizationId="
                + organizationId.orElse("<platform>")
                + ", connectionId=" + connectionId
                + ", publicHandle=" + publicHandle.value()
                + ", revisionId=" + revisionId
                + ", revision=" + revision
                + ", adapterKey=" + adapterKey.value()
                + ", adapterContractVersion=" + adapterContractVersion
                + ", configSchemaVersion=" + configSchemaVersion
                + ", capabilitiesJson=<redacted>, typedConfigJson=<redacted>, secretBindingVersion="
                + (secretBindingVersion.isPresent() ? "present" : "none") + "]";
    }
}
