package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterDescriptor;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import java.util.Objects;
import java.util.Optional;

/** Validated, versioned and persistence-ready configuration emitted by a reviewed Adapter. */
public record PreparedLoginConnectionRevision(
        AdapterDescriptor adapterDescriptor,
        String capabilitiesJson,
        String typedConfigJson,
        Optional<SecretPurpose> secretPurpose
) {

    public PreparedLoginConnectionRevision {
        Objects.requireNonNull(adapterDescriptor, "adapterDescriptor");
        capabilitiesJson = requireJson(capabilitiesJson, '[', ']', "capabilitiesJson");
        typedConfigJson = requireJson(typedConfigJson, '{', '}', "typedConfigJson");
        secretPurpose = Objects.requireNonNull(secretPurpose, "secretPurpose");
    }

    private static String requireJson(String value, char open, char close, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.length() < 2
                || normalized.charAt(0) != open
                || normalized.charAt(normalized.length() - 1) != close) {
            throw new IllegalArgumentException(field + " has an invalid JSON shape");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "PreparedLoginConnectionRevision[adapter="
                + adapterDescriptor.adapterKey().value()
                + ", capabilitiesJson=<redacted>, typedConfigJson=<redacted>, secretPurpose="
                + secretPurpose.map(SecretPurpose::value).orElse("none") + "]";
    }
}
