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
        ClientRegistration feishu = ClientRegistration.withRegistrationId("feishu")
                .clientId("cli_test123")
                .clientSecret("secret")
                .authorizationUri("https://open.feishu.cn/open-apis/authen/v1/authorize")
                .tokenUri("https://open.feishu.cn/open-apis/authen/v2/oauth/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .userNameAttributeName("open_id")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientName("飞书")
                .build();
        OAuthLoginFlowService oauthLoginFlowService = new OAuthLoginFlowService(
                java.util.List.of(),
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        resolver = new SkillHubOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(github, feishu),
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
    void resolve_ignoresUnsafeReturnTo() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "https://evil.example");

        resolver.resolve(request, "github");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void resolve_feishu_usesAppIdInsteadOfClientId() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/feishu");

        var authorizationRequest = resolver.resolve(request, "feishu");

        assertThat(authorizationRequest).isNotNull();
        String uri = authorizationRequest.getAuthorizationRequestUri();
        assertThat(uri).contains("app_id=cli_test123");
        assertThat(uri).contains("response_type=code");
        assertThat(uri).contains("state=");
        assertThat(uri).doesNotContain("client_id=");
        assertThat(uri).doesNotContain("scope=");
    }

    @Test
    void resolve_github_keepsStandardParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");

        var authorizationRequest = resolver.resolve(request, "github");

        assertThat(authorizationRequest).isNotNull();
        String uri = authorizationRequest.getAuthorizationRequestUri();
        assertThat(uri).contains("client_id=client");
        assertThat(uri).contains("scope=read:user");
        assertThat(uri).doesNotContain("app_id=");
    }
}
