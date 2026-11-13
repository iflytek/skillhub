package com.iflytek.skillhub.auth.rbac;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

/**
 * Serializable authenticated principal shared across session, OAuth, and API-token flows.
 */
public record PlatformPrincipal(
    String userId,
    String displayName,
    String email,
    String avatarUrl,
    String oauthProvider,
    Set<String> platformRoles,
    Set<String> tokenCapabilities,
    boolean legacyCompat,
    Instant tokenExpiresAt
) implements Serializable {
    public PlatformPrincipal {
        tokenCapabilities = "api_token".equals(oauthProvider)
                ? tokenCapabilities == null ? Set.of() : Set.copyOf(tokenCapabilities)
                : null;
    }

    public PlatformPrincipal(String userId, String displayName, String email, String avatarUrl,
                             String oauthProvider, Set<String> platformRoles) {
        this(userId, displayName, email, avatarUrl, oauthProvider, platformRoles, null, false, null);
    }

    public PlatformPrincipal(String userId, String displayName, String email, String avatarUrl,
                             String oauthProvider, Set<String> platformRoles, Set<String> tokenCapabilities) {
        this(userId, displayName, email, avatarUrl, oauthProvider, platformRoles, tokenCapabilities, false, null);
    }
}
