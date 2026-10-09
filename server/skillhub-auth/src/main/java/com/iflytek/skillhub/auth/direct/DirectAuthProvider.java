package com.iflytek.skillhub.auth.direct;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

/**
 * Extension point for username/password style direct authentication sources.
 */
public interface DirectAuthProvider {

    String providerCode();

    default String displayName() {
        return providerCode();
    }

    /**
     * Whether the provider can currently authenticate users. Unavailable providers are left out
     * of the auth method catalog.
     */
    default boolean isAvailable() {
        return true;
    }

    PlatformPrincipal authenticate(DirectAuthRequest request);
}
