package com.iflytek.skillhub.auth.connection.secret;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Coordinates cryptographically bound into a Secret envelope as authenticated data. */
public record SecretBindingContext(
        String scopeKey,
        String connectionId,
        SecretPurpose purpose,
        long bindingVersion
) {

    public SecretBindingContext {
        scopeKey = requireText(scopeKey, "scopeKey");
        connectionId = requireText(connectionId, "connectionId");
        purpose = Objects.requireNonNull(purpose, "purpose");
        if (bindingVersion < 1) {
            throw new IllegalArgumentException("bindingVersion must be positive");
        }
    }

    byte[] authenticatedData(String algorithm, String keyId) {
        return String.join(
                "\n",
                "skillhub-login-secret-v1",
                scopeKey,
                connectionId,
                purpose.value(),
                Long.toString(bindingVersion),
                requireText(algorithm, "algorithm"),
                requireText(keyId, "keyId")
        ).getBytes(StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
