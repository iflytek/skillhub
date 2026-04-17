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
import static org.mockito.Mockito.eq;
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
    void loadLoginContext_withGoogleRegistration_evaluatesPolicyAndBindsIdentity() {
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaims claims = new OAuthClaims(
                "google",
                "google-subject-1",
                "user@example.com",
                true,
                "Google User",
                Map.of("sub", "google-subject-1")
        );
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1",
                "Google User",
                "user@example.com",
                null,
                "google",
                Set.of("USER")
        );
        OAuthLoginFlowService service = serviceWithDelegate(extractor, accessPolicy, identityBindingService, upstreamUser());

        when(extractor.getProvider()).thenReturn("google");
        when(extractor.extract(any(OAuth2UserRequest.class), any(OAuth2User.class))).thenReturn(claims);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenReturn(principal);

        OAuthLoginFlowService.AuthenticatedLoginContext context = service.loadLoginContext(oauth2UserRequest("google"));

        assertThat(context.principal()).isEqualTo(principal);
        verify(accessPolicy).evaluate(claims);
        verify(identityBindingService).bindOrCreate(claims, UserStatus.ACTIVE);
        verify(identityBindingService, never()).createPendingUserIfAbsent(any(OAuthClaims.class));
    }

    @Test
    void loadLoginContext_withGooglePendingDecision_throwsPendingAndKeepsPolicyFlow() {
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaims claims = new OAuthClaims(
                "google",
                "google-subject-2",
                "pending@example.com",
                true,
                "Pending User",
                Map.of("sub", "google-subject-2")
        );
        OAuthLoginFlowService service = serviceWithDelegate(extractor, accessPolicy, identityBindingService, upstreamUser());

        when(extractor.getProvider()).thenReturn("google");
        when(extractor.extract(any(OAuth2UserRequest.class), any(OAuth2User.class))).thenReturn(claims);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.loadLoginContext(oauth2UserRequest("google")))
                .isInstanceOf(AccountPendingException.class);
        assertThat(service.resolveFailureRedirect(new AccountPendingException(), "/dashboard/publish"))
                .isEqualTo("/pending-approval");

        verify(identityBindingService).createPendingUserIfAbsent(claims);
        verify(identityBindingService, never()).bindOrCreate(any(OAuthClaims.class), eq(UserStatus.ACTIVE));
    }

    @Test
    void loadLoginContext_withGoogleDisabledAccount_returnsAccessDeniedRedirect() {
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaims claims = new OAuthClaims(
                "google",
                "google-subject-3",
                "disabled@example.com",
                true,
                "Disabled User",
                Map.of("sub", "google-subject-3")
        );
        OAuthLoginFlowService service = serviceWithDelegate(extractor, accessPolicy, identityBindingService, upstreamUser());

        when(extractor.getProvider()).thenReturn("google");
        when(extractor.extract(any(OAuth2UserRequest.class), any(OAuth2User.class))).thenReturn(claims);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenThrow(new AccountDisabledException());

        assertThatThrownBy(() -> service.loadLoginContext(oauth2UserRequest("google")))
                .isInstanceOf(AccountDisabledException.class);
        assertThat(service.resolveFailureRedirect(new AccountDisabledException(), "/dashboard/publish"))
                .isEqualTo("/access-denied");
    }

    @Test
    void loadLoginContext_withGoogleConflictLinking_returnsSafeAccessDenied() {
        OAuthClaimsExtractor extractor = mock(OAuthClaimsExtractor.class);
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        OAuthClaims claims = new OAuthClaims(
                "google",
                "google-subject-4",
                "conflict@example.com",
                true,
                "Conflict User",
                Map.of("sub", "google-subject-4")
        );
        OAuthLoginFlowService service = serviceWithDelegate(extractor, accessPolicy, identityBindingService, upstreamUser());
        OAuth2AuthenticationException conflictException =
                new OAuth2AuthenticationException(new OAuth2Error("access_denied", "Identity conflict", null));

        when(extractor.getProvider()).thenReturn("google");
        when(extractor.extract(any(OAuth2UserRequest.class), any(OAuth2User.class))).thenReturn(claims);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenThrow(conflictException);

        assertThatThrownBy(() -> service.loadLoginContext(oauth2UserRequest("google")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(throwable -> ((OAuth2AuthenticationException) throwable).getError().getErrorCode())
                .isEqualTo("access_denied");

        assertThat(service.resolveFailureRedirect(conflictException, "/dashboard/publish"))
                .isEqualTo("/access-denied");
        verify(identityBindingService).bindOrCreate(claims, UserStatus.ACTIVE);
        verify(identityBindingService, never()).createPendingUserIfAbsent(any(OAuthClaims.class));
    }

    private OAuthLoginFlowService serviceWithDelegate(OAuthClaimsExtractor extractor,
                                                      AccessPolicy accessPolicy,
                                                      IdentityBindingService identityBindingService,
                                                      OAuth2User upstreamUser) {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                accessPolicy,
                identityBindingService
        );
        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        when(delegate.loadUser(any(OAuth2UserRequest.class))).thenReturn(upstreamUser);
        ReflectionTestUtils.setField(service, "delegate", delegate);
        return service;
    }

    private OAuth2UserRequest oauth2UserRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://accounts.example.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.example.com/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://openidconnect.example.com/v1/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .clientName("Google")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300)
        );

        return new OAuth2UserRequest(registration, accessToken);
    }

    private OAuth2User upstreamUser() {
        return new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "sub", "google-subject-upstream",
                        "email", "upstream@example.com",
                        "email_verified", true,
                        "name", "Upstream User"
                ),
                "sub"
        );
    }
}
