package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * OAuth authentication exception raised when the mapped platform account was merged.
 */
public class AccountMergedException extends OAuth2AuthenticationException {

    public AccountMergedException() {
        super(new OAuth2Error("account_merged", "Account was merged", null));
    }
}
