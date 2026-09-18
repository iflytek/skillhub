package com.iflytek.skillhub.auth.oauth;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code openid} scope DingTalk's authorize endpoint requires.
 *
 * <p>The scope cannot simply be declared in {@code application.yml}: Spring Security treats a
 * registration carrying {@code openid} as an OIDC client and attaches a {@code nonce} parameter,
 * which DingTalk rejects. Adding the scope here keeps the registration a plain OAuth2 client while
 * still sending the parameter DingTalk expects.
 */
@Component
public class DingTalkAuthorizationRequestCustomizer implements ProviderAuthorizationRequestCustomizer {

    @Override
    public String getProvider() {
        return DingTalkOAuth2Constants.REGISTRATION_ID;
    }

    @Override
    public void customize(OAuth2AuthorizationRequest.Builder builder) {
        Set<String> scopes = new LinkedHashSet<>(builder.build().getScopes());
        scopes.add(DingTalkOAuth2Constants.AUTHORIZATION_SCOPE);
        builder.scopes(scopes);
    }
}
