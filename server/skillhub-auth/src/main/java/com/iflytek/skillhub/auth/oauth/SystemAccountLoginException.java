package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * OAuth authentication exception raised when an external identity resolves to a system account.
 */
public class SystemAccountLoginException extends OAuth2AuthenticationException {

    public SystemAccountLoginException() {
        super(new OAuth2Error(
                "system_account_forbidden",
                "System accounts cannot use interactive OAuth login",
                null
        ));
    }
}
