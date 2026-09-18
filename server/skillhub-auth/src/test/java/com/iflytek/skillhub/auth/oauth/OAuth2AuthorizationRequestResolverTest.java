package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2AuthorizationRequestResolverTest {

    private SkillHubOAuth2AuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        ClientRegistration github = ClientRegistration.withRegistrationId("github")
                .clientId("client")
                .clientSecret("secret")
                .authorizationUri("https://example.test/oauth/authorize")
                .tokenUri("https://example.test/oauth/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("id")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("read:user")
                .clientName("GitHub")
                .build();
        OAuthLoginFlowService oauthLoginFlowService = new OAuthLoginFlowService(
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        resolver = new SkillHubOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(github),
                oauthLoginFlowService
        );
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
    void resolve_keepsReturnToOnNonAuthorizationRequests() {
        // The redirect filter runs the resolver on every request in the chain, the provider
        // callback included. That request carries no returnTo, so treating it as an
        // authorization request would clear the target before the success handler reads it.
        MockHttpServletRequest authorization = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        authorization.setParameter("returnTo", "/device");
        resolver.resolve(authorization, "github");
        HttpSession session = authorization.getSession(false);

        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/github");
        callback.setParameter("code", "auth-code");
        callback.setSession(session);

        assertThat(resolver.resolve(callback)).isNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE))
                .isEqualTo("/device");
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
    void resolve_sendsDingTalkScopeOnTheUriButKeepsTheRequestNonOidc() {
        SkillHubOAuth2AuthorizationRequestResolver dingTalkResolver = resolverFor(
                dingTalkRegistration(),
                new DingTalkAuthorizationRequestCustomizer()
        );
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/oauth2/authorization/dingtalk");

        var authorizationRequest = dingTalkResolver.resolve(request, "dingtalk");

        assertThat(authorizationRequest).isNotNull();
        // DingTalk's authorize endpoint requires scope=openid on the wire.
        assertThat(authorizationRequest.getAuthorizationRequestUri()).contains("scope=openid");

        // But getScopes() must stay empty. OAuth2LoginAuthenticationProvider.authenticate returns
        // null when the authorization request's scopes contain "openid", which hands the callback to
        // OidcAuthorizationCodeAuthenticationProvider; that then fails with invalid_id_token because
        // DingTalk returns no id_token, and neither the token client nor the user service is reached.
        assertThat(authorizationRequest.getScopes()).doesNotContain("openid");

        // And no nonce: a registration declaring openid in configuration would get one attached,
        // which DingTalk also rejects.
        assertThat(authorizationRequest.getAdditionalParameters()).doesNotContainKey("nonce");
        assertThat(authorizationRequest.getAttributes()).doesNotContainKey("nonce");
        assertThat(authorizationRequest.getAuthorizationRequestUri()).doesNotContain("nonce=");

        // client-secret-post rather than none, so Spring does not apply PKCE. The DingTalk token
        // request sends no code_verifier, so a challenge on the authorize URI could not be answered.
        assertThat(authorizationRequest.getAuthorizationRequestUri()).doesNotContain("code_challenge");
    }

    @Test
    void resolve_leavesOtherProvidersUntouchedWhenADingTalkCustomizerIsRegistered() {
        SkillHubOAuth2AuthorizationRequestResolver mixedResolver = resolverFor(
                githubRegistration(),
                new DingTalkAuthorizationRequestCustomizer()
        );
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/oauth2/authorization/github");

        var authorizationRequest = mixedResolver.resolve(request, "github");

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getScopes()).containsExactly("read:user");
    }

    private static SkillHubOAuth2AuthorizationRequestResolver resolverFor(
            ClientRegistration registration,
            ProviderAuthorizationRequestCustomizer customizer
    ) {
        OAuthLoginFlowService flowService = new OAuthLoginFlowService(
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        return new SkillHubOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(registration),
                flowService,
                java.util.List.of(customizer)
        );
    }

    private static ClientRegistration githubRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId("client")
                .clientSecret("secret")
                .authorizationUri("https://example.test/oauth/authorize")
                .tokenUri("https://example.test/oauth/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("id")
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("read:user")
                .clientName("GitHub")
                .build();
    }

    private static ClientRegistration dingTalkRegistration() {
        // Mirrors application.yml: no scope declared, so Spring keeps this a plain OAuth2 client.
        return ClientRegistration.withRegistrationId("dingtalk")
                .clientId("dingoauth_test")
                .clientSecret("secret")
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://api.dingtalk.com/v1.0/contact/users/me")
                .userNameAttributeName("unionId")
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(
                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientName("钉钉")
                .build();
    }
}
