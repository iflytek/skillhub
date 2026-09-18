package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.iflytek.skillhub.auth.federation.adapter.LegacyOAuthVerifiedFactsAdapter;
import com.iflytek.skillhub.auth.federation.association.ExternalIdentityRepository;
import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationStage;
import com.iflytek.skillhub.auth.federation.core.IdentityLoginResolution;
import com.iflytek.skillhub.auth.federation.migration.IdentityBindingReadMetrics;
import com.iflytek.skillhub.auth.federation.migration.LegacyIdentityBindingDualReader;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessDecision;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginFlowServiceTest {

    @Test
    void loadLoginContext_executesUserinfoThroughRemoteIdentityIoBoundary() {
        OAuthClaims claims = claims();
        OAuthClaimsExtractor extractor = new OAuthClaimsExtractor() {
            @Override
            public String getProvider() {
                return "github";
            }

            @Override
            public OAuthClaims extract(OAuth2UserRequest request, OAuth2User user) {
                return claims;
            }
        };
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock();
        AtomicInteger boundaryCalls = new AtomicInteger();
        RemoteIdentityIoExecutor remoteIdentityIo = new RemoteIdentityIoExecutor() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> operation) {
                boundaryCalls.incrementAndGet();
                return operation.get();
            }
        };
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                accessPolicy,
                identityBindingService,
                identityCore,
                delegate,
                remoteIdentityIo
        );
        OAuth2UserRequest request = oauthUserRequest();
        OAuth2User upstreamUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH_USER")),
                Map.of("id", "gh_1"),
                "id"
        );
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1", "alice", "alice@example.com", null, "github", Set.of("USER")
        );
        when(delegate.loadUser(request)).thenReturn(upstreamUser);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityCore.evaluate(claims)).thenReturn(LegacyPlatformIdentityDecision.legacy());
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenReturn(principal);

        OAuthLoginFlowService.AuthenticatedLoginContext result = service.loadLoginContext(request);

        assertThat(result.upstreamUser()).isSameAs(upstreamUser);
        assertThat(result.principal()).isSameAs(principal);
        assertThat(boundaryCalls).hasValue(1);
        verify(delegate).loadUser(request);
    }

    @Test
    void loadLoginContext_prefersProviderUserServiceOverrideInsideRemoteIoBoundary() {
        OAuthClaims claims = claims("feishu", "ou_1");
        OAuthClaimsExtractor extractor = new OAuthClaimsExtractor() {
            @Override
            public String getProvider() {
                return "feishu";
            }

            @Override
            public OAuthClaims extract(OAuth2UserRequest request, OAuth2User user) {
                return claims;
            }
        };
        OAuth2User overrideUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH_USER")),
                Map.of("open_id", "ou_1"),
                "open_id"
        );
        AtomicInteger boundaryCalls = new AtomicInteger();
        AtomicInteger overrideCallsInsideBoundary = new AtomicInteger();
        RemoteIdentityIoExecutor remoteIdentityIo = new RemoteIdentityIoExecutor() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> operation) {
                boundaryCalls.incrementAndGet();
                return operation.get();
            }
        };
        ProviderOAuth2UserService override = new ProviderOAuth2UserService() {
            @Override
            public String getProvider() {
                return "feishu";
            }

            @Override
            public OAuth2User loadUser(OAuth2UserRequest request) {
                // Records the boundary state at call time: a provider override must run inside the
                // remote-IO boundary, otherwise its HTTP call would hold the surrounding transaction.
                if (boundaryCalls.get() == 1) {
                    overrideCallsInsideBoundary.incrementAndGet();
                }
                return overrideUser;
            }
        };
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock();
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_2", "zhangsan", null, null, "feishu", Set.of("USER")
        );
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                List.of(override),
                accessPolicy,
                identityBindingService,
                identityCore,
                delegate,
                remoteIdentityIo
        );
        OAuth2UserRequest request = oauthUserRequest("feishu");
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityCore.evaluate(claims)).thenReturn(LegacyPlatformIdentityDecision.legacy());
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenReturn(principal);

        OAuthLoginFlowService.AuthenticatedLoginContext result = service.loadLoginContext(request);

        assertThat(result.upstreamUser()).isSameAs(overrideUser);
        assertThat(result.principal()).isSameAs(principal);
        assertThat(boundaryCalls).hasValue(1);
        assertThat(overrideCallsInsideBoundary).hasValue(1);
        // The default user service must not be consulted when an override claims the registration.
        verify(delegate, never()).loadUser(request);
    }

    @Test
    void loadLoginContext_fallsBackToDefaultUserServiceForUnclaimedProviders() {
        OAuthClaims claims = claims();
        OAuthClaimsExtractor extractor = new OAuthClaimsExtractor() {
            @Override
            public String getProvider() {
                return "github";
            }

            @Override
            public OAuthClaims extract(OAuth2UserRequest request, OAuth2User user) {
                return claims;
            }
        };
        ProviderOAuth2UserService unrelatedOverride = new ProviderOAuth2UserService() {
            @Override
            public String getProvider() {
                return "feishu";
            }

            @Override
            public OAuth2User loadUser(OAuth2UserRequest request) {
                throw new AssertionError("Feishu override must not handle a GitHub login");
            }
        };
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock();
        OAuth2User upstreamUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH_USER")),
                Map.of("id", "gh_1"),
                "id"
        );
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1", "alice", "alice@example.com", null, "github", Set.of("USER")
        );
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(extractor),
                List.of(unrelatedOverride),
                accessPolicy,
                identityBindingService,
                identityCore,
                delegate,
                directRemoteIo()
        );
        OAuth2UserRequest request = oauthUserRequest();
        when(delegate.loadUser(request)).thenReturn(upstreamUser);
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityCore.evaluate(claims)).thenReturn(LegacyPlatformIdentityDecision.legacy());
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenReturn(principal);

        OAuthLoginFlowService.AuthenticatedLoginContext result = service.loadLoginContext(request);

        assertThat(result.upstreamUser()).isSameAs(upstreamUser);
        verify(delegate).loadUser(request);
    }

    private static RemoteIdentityIoExecutor directRemoteIo() {
        return new RemoteIdentityIoExecutor() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> operation) {
                return operation.get();
            }
        };
    }

    @ParameterizedTest
    @EnumSource(IdentityCoreMode.class)
    void authenticate_preservesPrincipalAcrossLegacyShadowAndActiveModes(IdentityCoreMode mode) {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        IdentityBindingRepository bindingRepository = mock(IdentityBindingRepository.class);
        LegacyPlatformIdentityCore identityCore = new LegacyPlatformIdentityCoreBridge(
                ignored -> mode,
                new LegacyIdentityBindingDualReader(
                        mock(ExternalIdentityRepository.class),
                        bindingRepository,
                        IdentityBindingReadMetrics.noop()
                ),
                new LegacyOAuthVerifiedFactsAdapter(Clock.systemUTC())
        );
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(), accessPolicy, identityBindingService, identityCore
        );
        OAuthClaims claims = claims();
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1", "alice", "alice@example.com", null, "github", Set.of("USER")
        );
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenReturn(principal);

        assertThat(service.authenticate(claims)).isSameAs(principal);

        verify(identityBindingService).bindOrCreate(claims, UserStatus.ACTIVE);
    }

    @Test
    void authenticate_allowsApprovedClaimsAndReturnsPlatformPrincipal() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(), accessPolicy, identityBindingService, identityCore
        );
        OAuthClaims claims = claims();
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1", "alice", "alice@example.com", null, "github", Set.of("USER")
        );
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityCore.evaluate(claims)).thenReturn(LegacyPlatformIdentityDecision.legacy());
        when(identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE)).thenReturn(principal);

        assertThat(service.authenticate(claims)).isSameAs(principal);
        var ordered = inOrder(identityCore, identityBindingService);
        ordered.verify(identityCore).evaluate(claims);
        ordered.verify(identityBindingService).bindOrCreate(claims, UserStatus.ACTIVE);
    }

    @Test
    void authenticate_createsPendingIdentityWithoutCreatingActivePrincipal() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(), accessPolicy, identityBindingService, identityCore
        );
        OAuthClaims claims = claims();
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.PENDING_APPROVAL);
        when(identityCore.evaluate(claims)).thenReturn(LegacyPlatformIdentityDecision.legacy());

        assertThatThrownBy(() -> service.authenticate(claims))
                .isInstanceOf(AccountPendingException.class);

        verify(identityCore).evaluate(claims);
        verify(identityBindingService).createPendingUserIfAbsent(claims);
        verify(identityBindingService, never()).bindOrCreate(claims, UserStatus.ACTIVE);
    }

    @Test
    void authenticate_activeUnifiedCoreConflictFailsBeforeLegacyBindingWrite() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(), accessPolicy, identityBindingService, identityCore
        );
        OAuthClaims claims = claims();
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.ALLOW);
        when(identityCore.evaluate(claims)).thenReturn(LegacyPlatformIdentityDecision.evaluated(
                IdentityCoreMode.ACTIVE,
                new IdentityLoginResolution.Conflict(IdentityCorrelationStage.EXACT_BINDING, 2)
        ));

        assertThatThrownBy(() -> service.authenticate(claims))
                .isInstanceOfSatisfying(OAuthIdentityCoreException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("access_denied"));

        verify(identityBindingService, never()).bindOrCreate(claims, UserStatus.ACTIVE);
        verify(identityBindingService, never()).createPendingUserIfAbsent(claims);
    }

    @Test
    void authenticate_pendingApprovalStillFailsBeforePendingWriteWhenActiveCoreConflicts() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(), accessPolicy, identityBindingService, identityCore
        );
        OAuthClaims claims = claims();
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.PENDING_APPROVAL);
        when(identityCore.evaluate(claims)).thenReturn(LegacyPlatformIdentityDecision.evaluated(
                IdentityCoreMode.ACTIVE,
                new IdentityLoginResolution.Conflict(IdentityCorrelationStage.EXACT_BINDING, 2)
        ));

        assertThatThrownBy(() -> service.authenticate(claims))
                .isInstanceOf(OAuthIdentityCoreException.class);

        verify(identityBindingService, never()).createPendingUserIfAbsent(claims);
        verify(identityBindingService, never()).bindOrCreate(claims, UserStatus.ACTIVE);
    }

    @Test
    void authenticate_deniesClaimsWithoutCreatingOrBindingAnAccount() {
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        LegacyPlatformIdentityCore identityCore = mock(LegacyPlatformIdentityCore.class);
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(), accessPolicy, identityBindingService, identityCore
        );
        OAuthClaims claims = claims();
        when(accessPolicy.evaluate(claims)).thenReturn(AccessDecision.DENY);

        assertThatThrownBy(() -> service.authenticate(claims))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("access_denied"));

        verify(identityCore, never()).evaluate(claims);
        verify(identityBindingService, never()).createPendingUserIfAbsent(claims);
        verify(identityBindingService, never()).bindOrCreate(claims, UserStatus.ACTIVE);
    }

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
    void resolveFailureRedirect_mapsMergedAccountToAccessDenied() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        assertThat(service.resolveFailureRedirect(new AccountMergedException(), null))
                .isEqualTo("/access-denied");
    }

    @Test
    void resolveFailureRedirect_mapsSystemAccountToAccessDenied() {
        OAuthLoginFlowService service = new OAuthLoginFlowService(
                List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );

        assertThat(service.resolveFailureRedirect(new SystemAccountLoginException(), null))
                .isEqualTo("/access-denied");
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

    private static OAuthClaims claims() {
        return new OAuthClaims(
                "github", "gh_1", "alice@example.com", true, "alice", Map.of()
        );
    }

    private static OAuthClaims claims(String provider, String subject) {
        return new OAuthClaims(provider, subject, null, false, subject, Map.of());
    }

    private static OAuth2UserRequest oauthUserRequest() {
        return oauthUserRequest("github");
    }

    private static OAuth2UserRequest oauthUserRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://skillhub.example/login/oauth2/code/" + registrationId)
                .authorizationUri("https://github.example/oauth/authorize")
                .tokenUri("https://github.example/oauth/token")
                .userInfoUri("https://github.example/user")
                .userNameAttributeName("id")
                .build();
        Instant issuedAt = Instant.parse("2026-09-07T00:00:00Z");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                issuedAt,
                issuedAt.plusSeconds(300)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
