package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** Privacy-safe failure raised when ACTIVE unified-core evaluation cannot reach a safe decision. */
public final class OAuthIdentityCoreException extends OAuth2AuthenticationException {

    public OAuthIdentityCoreException(Throwable cause) {
        super(error(), "Unified identity evaluation failed", cause);
    }

    private static OAuth2Error error() {
        return new OAuth2Error("access_denied", "Unified identity evaluation failed", null);
    }
}
