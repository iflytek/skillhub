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

    public SkillHubOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        rememberReturnTo(request);
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        rememberReturnTo(request);
        return authorizationRequest;
    }

    private void rememberReturnTo(HttpServletRequest request) {
        String returnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(request.getParameter("returnTo"));
        if (returnTo == null) {
            request.getSession().removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
            return;
        }
        request.getSession().setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, returnTo);
    }
}
