package com.iflytek.skillhub.auth.rbac;

import java.io.Serializable;
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
    Set<String> tokenCapabilities
) implements Serializable {
    public PlatformPrincipal {
        tokenCapabilities = "api_token".equals(oauthProvider)
                ? tokenCapabilities == null ? Set.of() : Set.copyOf(tokenCapabilities)
                : null;
    }

    public PlatformPrincipal(String userId, String displayName, String email, String avatarUrl,
                             String oauthProvider, Set<String> platformRoles) {
        this(userId, displayName, email, avatarUrl, oauthProvider, platformRoles, null);
    }
}
