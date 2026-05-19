package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomOidcUserServiceTest {

    @Test
    void loadUser_mapsOidcClaimsThroughExistingLoginFlow() {
        OAuthLoginFlowService loginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock();
        CustomOidcUserService service = new CustomOidcUserService(loginFlowService, delegate);
        OidcUserRequest request = oidcRequest();
        OidcUser upstreamUser = oidcUser(Map.of(
                IdTokenClaimNames.SUB, "oidc-sub-1",
                "email", "user@example.com",
                "email_verified", true,
                "preferred_username", "preferred-user",
                "name", "Display User",
                "picture", "https://idp.example/avatar.png"
        ));
        PlatformPrincipal platformPrincipal = new PlatformPrincipal(
                "usr_1",
                "Preferred User",
                "user@example.com",
                "https://idp.example/avatar.png",
                "okta",
                Set.of("USER", "SUPER_ADMIN")
        );
        when(delegate.loadUser(request)).thenReturn(upstreamUser);
        when(loginFlowService.authenticate(any())).thenReturn(platformPrincipal);

        OidcUser loadedUser = service.loadUser(request);

        ArgumentCaptor<OAuthClaims> claimsCaptor = ArgumentCaptor.forClass(OAuthClaims.class);
        verify(loginFlowService).authenticate(claimsCaptor.capture());
        OAuthClaims claims = claimsCaptor.getValue();
        assertThat(claims.provider()).isEqualTo("okta");
        assertThat(claims.subject()).isEqualTo("oidc-sub-1");
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.providerLogin()).isEqualTo("preferred-user");
        assertThat(claims.extra()).containsEntry("avatar_url", "https://idp.example/avatar.png");

        assertThat((Object) loadedUser.getAttribute("platformPrincipal")).isEqualTo(platformPrincipal);
        assertThat((Object) loadedUser.getAttribute("providerLogin")).isEqualTo("usr_1");
        assertThat(loadedUser.getName()).isEqualTo("usr_1");
        assertThat(loadedUser.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER", "ROLE_SUPER_ADMIN");
    }

    @Test
    void toOAuthClaims_fallsBackToNameWhenPreferredUsernameIsMissing() {
        OAuthClaims claims = CustomOidcUserService.toOAuthClaims(
                oidcRequest(),
                oidcUser(Map.of(
                        IdTokenClaimNames.SUB, "subject-2",
                        "email", "fallback@example.com",
                        "email_verified", false,
                        "name", "Fallback Name"
                ))
        );

        assertThat(claims.provider()).isEqualTo("okta");
        assertThat(claims.subject()).isEqualTo("subject-2");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.providerLogin()).isEqualTo("Fallback Name");
    }

    @Test
    void toOAuthClaims_nullsEmailWhenNotVerified() {
        OAuthClaims claims = CustomOidcUserService.toOAuthClaims(
                oidcRequest(),
                oidcUser(Map.of(
                        IdTokenClaimNames.SUB, "subject-3",
                        "email", "unverified@example.com",
                        "email_verified", false,
                        "preferred_username", "unverified-user"
                ))
        );

        assertThat(claims.provider()).isEqualTo("okta");
        assertThat(claims.subject()).isEqualTo("subject-3");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.providerLogin()).isEqualTo("unverified-user");
    }

    @Test
    void toOAuthClaims_nullsEmailWhenEmailVerifiedClaimIsAbsent() {
        OAuthClaims claims = CustomOidcUserService.toOAuthClaims(
                oidcRequest(),
                oidcUser(Map.of(
                        IdTokenClaimNames.SUB, "subject-absent-verified",
                        "email", "maybe@example.com",
                        "preferred_username", "maybe-user"
                ))
        );

        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.providerLogin()).isEqualTo("maybe-user");
    }

    @Test
    void toOAuthClaims_throwsWhenSubIsMissing() {
        OidcUser user = mock(OidcUser.class);
        when(user.getClaims()).thenReturn(Map.of("email", "no-sub@example.com"));
        assertThatThrownBy(() -> CustomOidcUserService.toOAuthClaims(oidcRequest(), user))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("sub");
    }

    @Test
    void toOAuthClaims_throwsWhenSubIsBlank() {
        OidcUser user = mock(OidcUser.class);
        when(user.getClaims()).thenReturn(Map.of(IdTokenClaimNames.SUB, "   "));
        assertThatThrownBy(() -> CustomOidcUserService.toOAuthClaims(oidcRequest(), user))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("sub");
    }

    @Test
    void toOAuthClaims_fallsBackToSubWhenAllOtherFieldsMissing() {
        OAuthClaims claims = CustomOidcUserService.toOAuthClaims(
                oidcRequest(),
                oidcUser(Map.of(IdTokenClaimNames.SUB, "only-sub"))
        );

        assertThat(claims.subject()).isEqualTo("only-sub");
        assertThat(claims.providerLogin()).isEqualTo("only-sub");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
    }

    @Test
    void loadUser_deniedWhenOidcEmailNotVerifiedAndPolicyChecksEmail() {
        OAuthLoginFlowService loginFlowService = mock(OAuthLoginFlowService.class);
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock();
        CustomOidcUserService service = new CustomOidcUserService(loginFlowService, delegate);
        OidcUserRequest request = oidcRequest();
        OidcUser upstreamUser = oidcUser(Map.of(
                IdTokenClaimNames.SUB, "oidc-sub-unverified",
                "email", "user@company.com",
                "email_verified", false,
                "preferred_username", "unverified-user"
        ));
        when(delegate.loadUser(request)).thenReturn(upstreamUser);

        ArgumentCaptor<OAuthClaims> claimsCaptor = ArgumentCaptor.forClass(OAuthClaims.class);
        when(loginFlowService.authenticate(claimsCaptor.capture()))
                .thenThrow(new OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error("access_denied")));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class);

        OAuthClaims captured = claimsCaptor.getValue();
        assertThat(captured.email()).isNull();
        assertThat(captured.emailVerified()).isFalse();
        assertThat(captured.subject()).isEqualTo("oidc-sub-unverified");
    }

    private static OidcUserRequest oidcRequest() {
        Instant issuedAt = Instant.parse("2026-04-24T00:00:00Z");
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, "request-sub")
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                issuedAt,
                issuedAt.plusSeconds(300)
        );
        return new OidcUserRequest(clientRegistration(), accessToken, idToken);
    }

    private static OidcUser oidcUser(Map<String, Object> claims) {
        Instant issuedAt = Instant.parse("2026-04-24T00:00:00Z");
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                claims
        );
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("OIDC_USER")),
                idToken,
                new OidcUserInfo(claims)
        );
    }

    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("okta")
                .clientId("client")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://skillhub.example/login/oauth2/code/okta")
                .authorizationUri("https://idp.example/oauth2/v1/authorize")
                .tokenUri("https://idp.example/oauth2/v1/token")
                .jwkSetUri("https://idp.example/oauth2/v1/keys")
                .userInfoUri("https://idp.example/oauth2/v1/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .scope("openid", "profile", "email")
                .build();
    }
}
