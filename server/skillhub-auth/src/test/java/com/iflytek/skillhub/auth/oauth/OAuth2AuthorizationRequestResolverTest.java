package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2AuthorizationRequestResolverTest {

    private SkillHubOAuth2AuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        ClientRegistration github = clientRegistration("github", "read:user");
        ClientRegistration gitlab = clientRegistration("gitlab", "read_user");
        ClientRegistration dingtalk = clientRegistration("dingtalk", "openid");
        ClientRegistration oidc = clientRegistration("oidc", "openid");
        OAuthLoginFlowService oauthLoginFlowService = new OAuthLoginFlowService(
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        resolver = new SkillHubOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(github, gitlab, dingtalk, oidc),
                oauthLoginFlowService
        );
    }

    private static ClientRegistration clientRegistration(String registrationId, String scope) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("client")
                .clientSecret("secret")
                .authorizationUri("https://example.test/oauth/authorize")
                .tokenUri("https://example.test/oauth/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope(scope)
                .clientName(registrationId)
                .build();
    }

    @Test
    void resolve_storesSanitizedReturnToInSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "/dashboard/publish?draft=1");

        resolver.resolve(request, "github");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE))
                .isEqualTo("/dashboard/publish?draft=1");
    }

    @Test
    void resolve_ignoresUnsafeReturnTo() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "https://evil.example");

        resolver.resolve(request, "github");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void resolve_sendsDingTalkOpenIdScopeWithoutTriggeringOidc() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/dingtalk");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "dingtalk");

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAuthorizationRequestUri()).contains("scope=openid");
        assertThat(authorizationRequest.getScopes()).doesNotContain("openid");
        assertThat(authorizationRequest.getAdditionalParameters()).doesNotContainKey("nonce");
    }

    @Test
    void resolve_preservesStandardOAuth2ProviderScopes() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "github");

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getScopes()).containsExactly("read:user");
    }

    @Test
    void resolve_preservesGitLabOAuth2Scopes() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/gitlab");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "gitlab");

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getScopes()).containsExactly("read_user");
    }

    @Test
    void resolve_preservesOpenIdForRealOidcProviders() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "oidc");

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getScopes()).containsExactly("openid");
        assertThat(authorizationRequest.getAdditionalParameters()).containsKey("nonce");
    }
}
