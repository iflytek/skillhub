package com.iflytek.skillhub.auth.federation.core;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Protocol-neutral verified facts accepted by the identity decision module.
 * Raw credentials, tokens, protocol payloads and unverified claims never cross this boundary.
 */
public record IdentityAssertion(
        Optional<String> organizationId,
        String connectionId,
        URI issuer,
        SubjectRef subject,
        Optional<VerifiedEmail> email,
        Optional<String> loginName,
        Optional<String> displayName,
        Optional<String> avatarUrl,
        Set<Assurance> assurance,
        Instant authenticatedAt,
        Map<AttributeKey, NormalizedAttribute> attributes
) {

    public IdentityAssertion {
        organizationId = normalizeOptionalText(organizationId, "organizationId");
        connectionId = requireText(connectionId, "connectionId");
        Objects.requireNonNull(issuer, "issuer must not be null");
        if (!issuer.isAbsolute()) {
            throw new IllegalArgumentException("issuer must be an absolute URI");
        }
        Objects.requireNonNull(subject, "subject must not be null");
        email = Objects.requireNonNull(email, "email must not be null");
        loginName = normalizeOptionalText(loginName, "loginName");
        displayName = normalizeOptionalText(displayName, "displayName");
        avatarUrl = normalizeOptionalText(avatarUrl, "avatarUrl");
        assurance = Set.copyOf(Objects.requireNonNull(assurance, "assurance must not be null"));
        Objects.requireNonNull(authenticatedAt, "authenticatedAt must not be null");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
        attributes.forEach((key, attribute) -> {
            Objects.requireNonNull(key, "attribute map key must not be null");
            Objects.requireNonNull(attribute, "attribute must not be null");
            if (!key.equals(attribute.key())) {
                throw new IllegalArgumentException("attribute key must match its map key");
            }
        });
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
