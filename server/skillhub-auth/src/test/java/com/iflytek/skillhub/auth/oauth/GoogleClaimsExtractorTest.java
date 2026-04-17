package com.iflytek.skillhub.auth.oauth;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleClaimsExtractorTest {

    @Test
    void getProvider_returnsGoogleLiteral() {
        GoogleClaimsExtractor extractor = new GoogleClaimsExtractor();

        assertThat(extractor.getProvider()).isEqualTo("google");
    }

    @Test
    void extract_withVerifiedEmail_mapsClaims() {
        GoogleClaimsExtractor extractor = new GoogleClaimsExtractor();
        OAuthClaims claims = extractor.extract(
                oauth2UserRequest(),
                oauth2User(Map.of(
                        "sub", "google-subject-1",
                        "email", "user@example.com",
                        "email_verified", true,
                        "name", "Google User"
                ))
        );

        assertThat(claims.provider()).isEqualTo("google");
        assertThat(claims.subject()).isEqualTo("google-subject-1");
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.providerLogin()).isEqualTo("Google User");
        assertThat(claims.extra()).containsEntry("sub", "google-subject-1");
    }

    @Test
    void extract_withUnverifiedEmail_doesNotMarkEmailAsVerified() {
        GoogleClaimsExtractor extractor = new GoogleClaimsExtractor();
        OAuthClaims claims = extractor.extract(
                oauth2UserRequest(),
                oauth2User(Map.of(
                        "sub", "google-subject-2",
                        "email", "unverified@example.com",
                        "email_verified", false,
                        "name", "Unverified User"
                ))
        );

        assertThat(claims.subject()).isEqualTo("google-subject-2");
        assertThat(claims.email()).isEqualTo("unverified@example.com");
        assertThat(claims.emailVerified()).isFalse();
    }

    @Test
    void extract_withoutSub_throwsInvalidUserInfo() {
        GoogleClaimsExtractor extractor = new GoogleClaimsExtractor();

        assertThatThrownBy(() -> extractor.extract(
                oauth2UserRequest(),
                oauth2User(Map.of(
                        "email", "missing-sub@example.com",
                        "email_verified", true
                ))
        ))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(throwable -> ((OAuth2AuthenticationException) throwable).getError().getErrorCode())
                .isEqualTo("invalid_user_info");
    }

    private OAuth2UserRequest oauth2UserRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .clientName("Google")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300)
        );
        return new OAuth2UserRequest(registration, token);
    }

    private OAuth2User oauth2User(Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(), attributes, "sub");
    }
}
