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
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

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
    void extract_allowsNullEmailAndLeavesDisplayNameUnsetWhenFeishuSendsNoName() {
        Map<String, Object> attrs = new HashMap<>(Map.of("open_id", "ou_456"));

        OAuthClaims claims = extractor.extract(userRequest(), user(attrs));

        assertThat(claims.subject()).isEqualTo("ou_456");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        // Must not synthesize "feishu-<open_id>": providerLogin is written to displayName and into
        // UserActivatedEvent, so a synthesized value would carry the subject into event consumers.
        assertThat(claims.providerLogin()).isNull();
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

    @Test
    void extract_rejectsBlankOpenId() {
        // Blank must fail rather than become a subject. DefaultOAuth2User already rejects a
        // wholly absent open_id, so a permissive OAuth2User is used to test this contract
        // directly instead of relying on that upstream guard.
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("open_id", "   ");
        attrs.put("name", "张三");

        assertThatThrownBy(() -> extractor.extract(userRequest(), permissiveUser(attrs)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("open_id");
    }

    /** An {@link OAuth2User} that does not enforce the name attribute, unlike DefaultOAuth2User. */
    private OAuth2User permissiveUser(Map<String, Object> attrs) {
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
                return String.valueOf(attrs.get("open_id"));
            }
        };
    }

    @Test
    void extract_doesNotPromoteUnionIdToSubject() {
        // union_id stays in extra: a subject that can change between logins would split one
        // person across two platform accounts.
        Map<String, Object> attrs = new HashMap<>(Map.of(
                "open_id", "ou_abc",
                "union_id", "on_xyz"
        ));

        OAuthClaims claims = extractor.extract(userRequest(), user(attrs));

        assertThat(claims.subject()).isEqualTo("ou_abc");
        assertThat(claims.extra()).containsEntry("union_id", "on_xyz");
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
                .authorizationUri("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
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
