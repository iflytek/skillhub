package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

class DingTalkClaimsExtractorTest {

    private final DingTalkClaimsExtractor extractor = new DingTalkClaimsExtractor();

    @Test
    void extract_mapsUnionIdAndNick() {
        Map<String, Object> attrs = new HashMap<>(Map.of(
                "unionId", "un_123",
                "nick", "张三",
                "email", "zhangsan@corp.example"
        ));

        OAuthClaims claims = extractor.extract(userRequest(), user(attrs));

        assertThat(claims.provider()).isEqualTo("dingtalk");
        assertThat(claims.subject()).isEqualTo("un_123");
        assertThat(claims.providerLogin()).isEqualTo("张三");
        assertThat(claims.email()).isEqualTo("zhangsan@corp.example");
        // DingTalk's contact endpoint does not attest email ownership.
        assertThat(claims.emailVerified()).isFalse();
    }

    @Test
    void extract_neverAcceptsOpenIdOrUserIdAsSubject() {
        // openId is per-app and userId per-organization. Accepting either as a fallback would bind
        // a different identity than a later login carrying unionId, splitting one person across
        // two platform accounts.
        Map<String, Object> attrs = new HashMap<>(Map.of(
                "openId", "op_456",
                "userId", "usr_789",
                "nick", "张三"
        ));

        assertThatThrownBy(() -> extractor.extract(userRequest(), user(attrs)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("unionId");
    }

    @Test
    void extract_rejectsBlankUnionId() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("unionId", "   ");
        attrs.put("nick", "张三");

        assertThatThrownBy(() -> extractor.extract(userRequest(), user(attrs)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("unionId");
    }

    @Test
    void extract_fallsBackToNameThenLeavesDisplayNameUnset() {
        Map<String, Object> withName = new HashMap<>(Map.of("unionId", "un_1", "name", "Alice"));
        assertThat(extractor.extract(userRequest(), user(withName)).providerLogin()).isEqualTo("Alice");

        // Must not synthesize from the subject: providerLogin is written to displayName and into
        // UserActivatedEvent, so a synthesized value would carry the subject to event consumers.
        Map<String, Object> bare = new HashMap<>(Map.of("unionId", "un_2"));
        OAuthClaims claims = extractor.extract(userRequest(), user(bare));
        assertThat(claims.providerLogin()).isNull();
        assertThat(claims.subject()).isEqualTo("un_2");
    }

    /** Does not enforce the name attribute, unlike DefaultOAuth2User. */
    private OAuth2User user(Map<String, Object> attrs) {
        return new OAuth2User() {
            @Override
            public Map<String, Object> getAttributes() {
                return attrs;
            }

            @Override
            public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority>
                    getAuthorities() {
                return java.util.List.of();
            }

            @Override
            public String getName() {
                return String.valueOf(attrs.get("unionId"));
            }
        };
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("dingtalk")
                .clientId("dingoauth_test")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .userInfoUri("https://api.dingtalk.com/v1.0/contact/users/me")
                .userNameAttributeName("unionId")
                .clientName("钉钉")
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
