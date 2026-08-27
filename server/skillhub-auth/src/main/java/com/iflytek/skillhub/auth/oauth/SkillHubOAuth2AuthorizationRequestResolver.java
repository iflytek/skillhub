package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 authorization request resolver that preserves a sanitized post-login redirect target in
 * the HTTP session.
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
        return rememberIfAuthorizationRequest(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return rememberIfAuthorizationRequest(request, delegate.resolve(request, clientRegistrationId));
    }

    /**
     * {@code OAuth2AuthorizationRequestRedirectFilter} calls the resolver on every request in the
     * chain, not only on authorization requests; the delegate simply answers null for the rest.
     * Recording the return target on those calls would clear it again on the very next request —
     * including the provider callback, which carries no {@code returnTo} and is processed by this
     * filter before authentication succeeds. Only an actual authorization request may touch it.
     */
    private OAuth2AuthorizationRequest rememberIfAuthorizationRequest(
            HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest != null) {
            oauthLoginFlowService.rememberReturnTo(request);
        }
        return authorizationRequest;
    }
}
