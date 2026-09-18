package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Strategy interface for provider-specific authorization request tweaks, for providers whose
 * authorize endpoint deviates from the standard parameter contract.
 *
 * <p>The token and userinfo counterparts are {@link ProviderTokenResponseClient} and
 * {@link ProviderOAuth2UserService}.
 */
public interface ProviderAuthorizationRequestCustomizer {

    String getProvider();

    void customize(OAuth2AuthorizationRequest.Builder builder);
}
