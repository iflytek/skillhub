package com.iflytek.skillhub.auth.connection.oidc;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeConfig;
import com.iflytek.skillhub.auth.federation.core.SubjectType;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable non-secret OIDC client configuration pinned to one connection revision. */
public record OidcLoginConnectionRuntimeConfig(
        OidcIssuer issuer,
        String clientId,
        Set<String> scopes
) implements LoginConnectionRuntimeConfig {

    public static final AdapterKey ADAPTER_KEY = new AdapterKey("oidc-standard");
    public static final SubjectType SUBJECT_TYPE = new SubjectType("oidc-sub");
    private static final int MAX_SCOPES = 16;
    private static final int MAX_SCOPE_LENGTH = 128;

    public OidcLoginConnectionRuntimeConfig {
        issuer = Objects.requireNonNull(issuer, "issuer");
        clientId = requireClientId(clientId);
        TreeSet<String> normalizedScopes = new TreeSet<>();
        normalizedScopes.add("openid");
        for (String scope : Objects.requireNonNull(scopes, "scopes")) {
            normalizedScopes.add(requireScope(scope));
        }
        if (normalizedScopes.size() > MAX_SCOPES) {
            throw new IllegalArgumentException("OIDC scope count exceeds the safe limit");
        }
        scopes = Collections.unmodifiableSet(normalizedScopes);
    }

    String scopeValue() {
        return String.join(" ", scopes);
    }

    private static String requireClientId(String value) {
        Objects.requireNonNull(value, "clientId");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("OIDC client id is invalid");
        }
        return value;
    }

    private static String requireScope(String value) {
        Objects.requireNonNull(value, "scope");
        if (value.isEmpty() || value.length() > MAX_SCOPE_LENGTH) {
            throw new IllegalArgumentException("OIDC scope is invalid");
        }
        boolean invalid = value.chars().anyMatch(character ->
                character < 0x21 || character > 0x7e || character == '"' || character == '\\'
        );
        if (invalid) {
            throw new IllegalArgumentException("OIDC scope is invalid");
        }
        return value;
    }
}
