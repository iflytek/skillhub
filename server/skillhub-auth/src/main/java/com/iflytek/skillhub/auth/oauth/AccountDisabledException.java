package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * OAuth authentication exception raised when the mapped platform account is disabled.
 */
public class AccountDisabledException extends OAuth2AuthenticationException {

    public AccountDisabledException() {
        super(new OAuth2Error("account_disabled", "Account is disabled", null));
    }
}
