package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class DingTalkClaimsExtractorTest {

    private final DingTalkClaimsExtractor extractor = new DingTalkClaimsExtractor();

    @Test
    void extract_usesUnionIdAsSubject() {
        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "unionId", "union123",
                                "openId", "open456",
                                "nick", "测试用户"
                        ),
                        "unionId"
                )
        );

        assertThat(claims.provider()).isEqualTo("dingtalk");
        assertThat(claims.subject()).isEqualTo("union123");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.providerLogin()).isEqualTo("测试用户");
    }

    @Test
    void extract_fallsBackToOpenId() {
        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "openId", "open456",
                                "nick", "测试用户"
                        ),
                        "openId"
                )
        );

        assertThat(claims.subject()).isEqualTo("open456");
    }

    @Test
    void extract_fallsBackToUserIdWhenHigherPriorityIdentifiersAreBlank() {
        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "unionId", " ",
                                "openId", "",
                                "userId", "user789",
                                "nick", "测试用户"
                        ),
                        "userId"
                )
        );

        assertThat(claims.subject()).isEqualTo("user789");
    }

    @Test
    void extract_preservesRealEmailWithoutClaimingVerification() {
        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "unionId", "union123",
                                "email", "user@example.com"
                        ),
                        "unionId"
                )
        );

        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.emailVerified()).isFalse();
    }

    @Test
    void extract_throwsWhenAllStableIdentifiersAreMissing() {
        assertThatThrownBy(() -> extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of("nick", "测试用户"),
                        "nick"
                )
        )).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex)
                        .getError().getErrorCode()).isEqualTo("missing_subject"));
    }

    @Test
    void getProvider_returnsDingtalk() {
        assertThat(extractor.getProvider()).isEqualTo("dingtalk");
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("dingtalk")
                .clientId("dingzgzf3b9k7jv74iq2")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .userInfoUri("https://api.dingtalk.com/v1.0/contact/users/me")
                .userNameAttributeName("unionId")
                .clientName("钉钉")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
