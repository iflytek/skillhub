package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends the {@code scope=openid} parameter DingTalk's authorize endpoint requires, without letting
 * Spring Security classify the login as OIDC.
 *
 * <p>Two separate mechanisms keyed off {@code openid} have to be avoided, which is why the scope is
 * written onto the URI rather than into the request's scope set:
 *
 * <ul>
 *   <li>A registration declaring {@code openid} in configuration becomes an OIDC client, and
 *       {@code DefaultOAuth2AuthorizationRequestResolver} attaches a {@code nonce} that DingTalk
 *       rejects. Hence no scope in {@code application.yml}.
 *   <li>{@code OAuth2LoginAuthenticationProvider.authenticate} returns null when the authorization
 *       request's {@code getScopes()} contains {@code openid}, handing the callback to
 *       {@code OidcAuthorizationCodeAuthenticationProvider}, which then fails with
 *       {@code invalid_id_token} because DingTalk returns no {@code id_token}. Hence the scope set
 *       stays empty and only the outgoing URI carries the parameter.
 * </ul>
 */
@Component
public class DingTalkAuthorizationRequestCustomizer implements ProviderAuthorizationRequestCustomizer {

    @Override
    public String getProvider() {
        return DingTalkOAuth2Constants.REGISTRATION_ID;
    }

    @Override
    public void customize(OAuth2AuthorizationRequest.Builder builder) {
        String authorizationRequestUri = UriComponentsBuilder
                .fromUriString(builder.build().getAuthorizationRequestUri())
                .replaceQueryParam("scope", DingTalkOAuth2Constants.AUTHORIZATION_SCOPE)
                .build(true)
                .toUriString();
        builder.authorizationRequestUri(authorizationRequestUri);
    }
}
