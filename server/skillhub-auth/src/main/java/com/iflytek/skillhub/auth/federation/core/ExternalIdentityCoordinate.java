package com.iflytek.skillhub.auth.federation.core;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Stable coordinate used for exact binding and pre-provisioned subject lookup. */
public record ExternalIdentityCoordinate(
        Optional<String> organizationId,
        String connectionId,
        URI issuer,
        SubjectRef subject
) {

    public ExternalIdentityCoordinate {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        organizationId = organizationId.map(value -> requireText(value, "organizationId"));
        connectionId = requireText(connectionId, "connectionId");
        Objects.requireNonNull(issuer, "issuer must not be null");
        if (!issuer.isAbsolute()) {
            throw new IllegalArgumentException("issuer must be an absolute URI");
        }
        Objects.requireNonNull(subject, "subject must not be null");
    }

    public static ExternalIdentityCoordinate from(IdentityAssertion assertion) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        return new ExternalIdentityCoordinate(
                assertion.organizationId(),
                assertion.connectionId(),
                assertion.issuer(),
                assertion.subject()
        );
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
