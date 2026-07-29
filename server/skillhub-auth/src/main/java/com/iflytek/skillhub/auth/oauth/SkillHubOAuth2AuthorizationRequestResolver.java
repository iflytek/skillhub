package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth2 authorization request resolver that preserves a sanitized post-login
 * redirect target in the HTTP session.
 */
@Component
public class SkillHubOAuth2AuthorizationRequestResolver
        implements org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final OAuthLoginFlowService oauthLoginFlowService;

    public SkillHubOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
                                                      OAuthLoginFlowService oauthLoginFlowService) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
        this.oauthLoginFlowService = oauthLoginFlowService;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        oauthLoginFlowService.rememberReturnTo(request);
        return adaptDingTalkRequest(authorizationRequest);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        oauthLoginFlowService.rememberReturnTo(request);
        return adaptDingTalkRequest(authorizationRequest);
    }

    private static OAuth2AuthorizationRequest adaptDingTalkRequest(
            OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null
                || !DingTalkOAuth2Constants.REGISTRATION_ID.equals(
                        authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID))) {
            return authorizationRequest;
        }

        Set<String> oauth2Scopes = new LinkedHashSet<>(authorizationRequest.getScopes());
        oauth2Scopes.remove(DingTalkOAuth2Constants.AUTHORIZATION_SCOPE);
        String authorizationRequestUri = UriComponentsBuilder
                .fromUriString(authorizationRequest.getAuthorizationRequestUri())
                .replaceQueryParam(OidcParameterNames.NONCE)
                .build(true)
                .toUriString();
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .scopes(oauth2Scopes)
                .additionalParameters(parameters -> parameters.remove(OidcParameterNames.NONCE))
                .attributes(attributes -> attributes.remove(OidcParameterNames.NONCE))
                .authorizationRequestUri(authorizationRequestUri)
                .build();
    }
}
