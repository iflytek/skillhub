package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessDecision;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginFlowServiceTest {

    @Test
    void rememberReturnTo_stores_sanitized_return_target() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("returnTo", "/dashboard/publish");

        service.rememberReturnTo(request);

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE))
                .isEqualTo("/dashboard/publish");
    }

    @Test
    void rememberReturnTo_nullReturnTo_removesAttribute() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/old");

        service.rememberReturnTo(request);

        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void resolveFailureRedirect_maps_access_denied_to_user_facing_page() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        String redirect = service.resolveFailureRedirect(
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")),
                "/settings/accounts"
        );

        assertThat(redirect).isEqualTo("/access-denied");
    }

    @Test
    void resolveFailureRedirect_accountPendingException_mapsToPendingApproval() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        String redirect = service.resolveFailureRedirect(new AccountPendingException(), "/dashboard");

        assertThat(redirect).isEqualTo("/pending-approval");
    }

    @Test
    void resolveFailureRedirect_accountDisabledException_mapsToAccessDenied() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        String redirect = service.resolveFailureRedirect(new AccountDisabledException(), "/dashboard");

        assertThat(redirect).isEqualTo("/access-denied");
    }

    @Test
    void resolveFailureRedirect_genericException_withReturnTo_mapsToLoginWithParam() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        String redirect = service.resolveFailureRedirect(
                new OAuth2AuthenticationException(new OAuth2Error("server_error")),
                "/settings/profile"
        );

        assertThat(redirect).isEqualTo("/login?returnTo=%2Fsettings%2Fprofile");
    }

    @Test
    void resolveFailureRedirect_genericException_withoutReturnTo_returnsNull() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        String redirect = service.resolveFailureRedirect(
                new OAuth2AuthenticationException(new OAuth2Error("server_error")),
                null
        );

        assertThat(redirect).isNull();
    }

    @Test
    void consumeReturnTo_clearsUnsafeSessionValue() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "https://evil.example");

        String returnTo = service.consumeReturnTo(session);

        assertThat(returnTo).isNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void consumeReturnTo_nullSession_returnsNull() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        String returnTo = service.consumeReturnTo(null);

        assertThat(returnTo).isNull();
    }

    @Test
    void consumeReturnTo_nonStringValue_returnsNull() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, 123);

        String returnTo = service.consumeReturnTo(session);

        assertThat(returnTo).isNull();
    }

    @Test
    void loadLoginContext_unsupportedProvider_throwsOAuth2AuthenticationException() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        when(extractor.getProvider()).thenReturn("gitlab");

        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                accessPolicy,
                identityBindingService
        );

        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        ReflectionTestUtils.setField(service, "delegate", delegate);

        OAuth2UserRequest request = userRequest("github");
        OAuth2User upstreamUser = new DefaultOAuth2User(List.of(), Map.of("login", "alice"), "login");
        when(delegate.loadUser(request)).thenReturn(upstreamUser);

        assertThatThrownBy(() -> service.loadLoginContext(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException oauth2Ex = (OAuth2AuthenticationException) ex;
                    assertThat(oauth2Ex.getError().getErrorCode()).isEqualTo("unsupported_provider");
                });
    }

    @Test
    void loadLoginContext_pendingApproval_createsPendingUserAndThrows() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        when(extractor.getProvider()).thenReturn("github");
        OAuthClaims claims = new OAuthClaims("github", "gh_1", "a@example.com", true, "alice", Map.of());
        when(extractor.extract(any(), any())).thenReturn(claims);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.PENDING_APPROVAL);

        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                accessPolicy,
                identityBindingService
        );

        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        ReflectionTestUtils.setField(service, "delegate", delegate);

        OAuth2UserRequest request = userRequest("github");
        OAuth2User upstreamUser = new DefaultOAuth2User(List.of(), Map.of("login", "alice"), "login");
        when(delegate.loadUser(request)).thenReturn(upstreamUser);

        assertThatThrownBy(() -> service.loadLoginContext(request))
                .isInstanceOf(AccountPendingException.class);

        verify(identityBindingService).createPendingUserIfAbsent(claims);
    }

    @Test
    void loadLoginContext_deniedAccess_throwsOAuth2AuthenticationException() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        when(extractor.getProvider()).thenReturn("github");
        OAuthClaims claims = new OAuthClaims("github", "gh_1", "a@example.com", true, "alice", Map.of());
        when(extractor.extract(any(), any())).thenReturn(claims);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.DENY);

        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                accessPolicy,
                identityBindingService
        );

        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        ReflectionTestUtils.setField(service, "delegate", delegate);

        OAuth2UserRequest request = userRequest("github");
        OAuth2User upstreamUser = new DefaultOAuth2User(List.of(), Map.of("login", "alice"), "login");
        when(delegate.loadUser(request)).thenReturn(upstreamUser);

        assertThatThrownBy(() -> service.loadLoginContext(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException oauth2Ex = (OAuth2AuthenticationException) ex;
                    assertThat(oauth2Ex.getError().getErrorCode()).isEqualTo("access_denied");
                });

        verify(identityBindingService, never()).bindOrCreate(any(), any());
    }

    @Test
    void loadLoginContext_allowed_returnsAuthenticatedContext() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        when(extractor.getProvider()).thenReturn("github");
        OAuthClaims claims = new OAuthClaims("github", "gh_1", "a@example.com", true, "alice", Map.of());
        when(extractor.extract(any(), any())).thenReturn(claims);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);

        PlatformPrincipal principal = new PlatformPrincipal("usr_1", "Alice", "a@example.com", null, "github", Set.of("USER"));
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenReturn(principal);

        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                accessPolicy,
                identityBindingService
        );

        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        ReflectionTestUtils.setField(service, "delegate", delegate);

        OAuth2UserRequest request = userRequest("github");
        OAuth2User upstreamUser = new DefaultOAuth2User(List.of(), Map.of("login", "alice"), "login");
        when(delegate.loadUser(request)).thenReturn(upstreamUser);

        OAuthLoginFlowService.AuthenticatedLoginContext context = service.loadLoginContext(request);

        assertThat(context.upstreamUser()).isEqualTo(upstreamUser);
        assertThat(context.principal()).isEqualTo(principal);
    }

    private OAuth2UserRequest userRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read_user")
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .userInfoUri("https://example.com/api/user")
                .userNameAttributeName("login")
                .clientName("Test")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-123",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
