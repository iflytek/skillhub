package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import com.iflytek.skillhub.auth.connection.secret.SecretReference;
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
        Optional<SecretReference> secretReference
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
        secretReference = Objects.requireNonNull(secretReference, "secretReference");
        if (secretReference.isPresent()) {
            SecretReference reference = secretReference.get();
            String expectedScope = organizationId.orElse(SecretReference.PLATFORM_SCOPE_KEY);
            if (!expectedScope.equals(reference.scopeKey())
                    || !connectionId.equals(reference.connectionId())) {
                throw new IllegalArgumentException("Secret reference scope is invalid");
            }
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
                + ", capabilitiesJson=<redacted>, typedConfigJson=<redacted>, secretReference="
                + (secretReference.isPresent() ? "present" : "none") + "]";
    }
}
