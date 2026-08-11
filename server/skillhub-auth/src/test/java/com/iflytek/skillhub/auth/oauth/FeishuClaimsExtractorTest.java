package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class FeishuClaimsExtractorTest {

    private final FeishuClaimsExtractor extractor = new FeishuClaimsExtractor();

    @Test
    void extract_prefersEnterpriseEmailOverPersonalEmail() {
        Map<String, Object> attrs = new HashMap<>(Map.of(
                "open_id", "ou_123",
                "name", "张三",
                "email", "zhangsan@personal.example",
                "enterprise_email", "zhangsan@corp.example"
        ));

        OAuthClaims claims = extractor.extract(userRequest(), user(attrs));

        assertThat(claims.provider()).isEqualTo("feishu");
        assertThat(claims.subject()).isEqualTo("ou_123");
        assertThat(claims.email()).isEqualTo("zhangsan@corp.example");
        // Feishu emails are admin-imported; the extractor must not claim verification.
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.providerLogin()).isEqualTo("张三");
    }

    @Test
    void extract_allowsNullEmailAndFallsBackUsername() {
        Map<String, Object> attrs = new HashMap<>(Map.of("open_id", "ou_456"));

        OAuthClaims claims = extractor.extract(userRequest(), user(attrs));

        assertThat(claims.subject()).isEqualTo("ou_456");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.providerLogin()).isEqualTo("feishu-ou_456");
    }

    @Test
    void extract_fallsBackToEnglishNameWhenChineseNameBlank() {
        Map<String, Object> attrs = new HashMap<>(Map.of(
                "open_id", "ou_789",
                "en_name", "Alice"
        ));

        OAuthClaims claims = extractor.extract(userRequest(), user(attrs));

        assertThat(claims.providerLogin()).isEqualTo("Alice");
    }

    private DefaultOAuth2User user(Map<String, Object> attrs) {
        return new DefaultOAuth2User(java.util.List.of(), attrs, "open_id");
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("feishu")
                .clientId("cli_test123")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://open.feishu.cn/open-apis/authen/v1/authorize")
                .tokenUri("https://open.feishu.cn/open-apis/authen/v2/oauth/token")
                .userInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .userNameAttributeName("open_id")
                .clientName("飞书")
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
