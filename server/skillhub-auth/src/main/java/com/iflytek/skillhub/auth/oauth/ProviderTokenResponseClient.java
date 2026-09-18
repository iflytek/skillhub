package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;

/**
 * Strategy interface for provider-specific token exchange. Implementations override the default
 * exchange for providers whose token endpoints deviate from the standard form-urlencoded contract.
 *
 * <p>The userinfo counterpart is {@link ProviderOAuth2UserService}.
 */
public interface ProviderTokenResponseClient
        extends OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    String getProvider();
}
