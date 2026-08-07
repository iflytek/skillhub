package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth2 authorization request resolver that preserves a sanitized post-login redirect target in
 * the HTTP session.
 */
@Component
public class SkillHubOAuth2AuthorizationRequestResolver
        implements org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver {

    private static final String FEISHU_REGISTRATION_ID = "feishu";
    private static final String AUTHORIZATION_BASE_PATH = "/oauth2/authorization/";

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
        return customizeFeishu(authorizationRequest, registrationIdFrom(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        oauthLoginFlowService.rememberReturnTo(request);
        return customizeFeishu(authorizationRequest, clientRegistrationId);
    }

    private String registrationIdFrom(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int index = uri.indexOf(AUTHORIZATION_BASE_PATH);
        if (index < 0) {
            return null;
        }
        return uri.substring(index + AUTHORIZATION_BASE_PATH.length());
    }

    /**
     * Feishu's authorize endpoint identifies the client with {@code app_id} rather than
     * {@code client_id}, and scopes are controlled by the app's permission configuration rather
     * than a {@code scope} request parameter.
     */
    private OAuth2AuthorizationRequest customizeFeishu(OAuth2AuthorizationRequest authorizationRequest,
                                                       String registrationId) {
        if (authorizationRequest == null || !FEISHU_REGISTRATION_ID.equals(registrationId)) {
            return authorizationRequest;
        }
        String authorizationUri = UriComponentsBuilder
                .fromUriString(authorizationRequest.getAuthorizationUri())
                .queryParam("app_id", authorizationRequest.getClientId())
                .queryParam("redirect_uri", authorizationRequest.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", authorizationRequest.getState())
                .build()
                .toUriString();
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationRequest.getAuthorizationUri())
                .clientId(authorizationRequest.getClientId())
                .redirectUri(authorizationRequest.getRedirectUri())
                .scopes(authorizationRequest.getScopes())
                .state(authorizationRequest.getState())
                .attributes(attributes -> attributes.putAll(authorizationRequest.getAttributes()))
                .authorizationRequestUri(authorizationUri)
                .build();
    }
}
