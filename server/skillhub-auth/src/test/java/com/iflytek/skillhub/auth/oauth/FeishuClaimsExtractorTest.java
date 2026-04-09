package com.iflytek.skillhub.auth.oauth;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TDD RED phase tests for FeishuClaimsExtractor.
 * These tests define expected behavior for the feishu OAuth claims extraction.
 */
class FeishuClaimsExtractorTest {

    private final FeishuClaimsExtractor extractor = new FeishuClaimsExtractor();

    @Test
    void getProvider_returnsFeishu() {
        assertThat(extractor.getProvider()).isEqualTo("feishu");
    }

    @Test
    void extract_withAllAttributes_returnsCorrectClaims() {
        // Given: OAuth2User with all feishu attributes
        Map<String, Object> attrs = Map.of(
            "open_id", "ou_123456",
            "union_id", "on_123456",
            "user_id", "123456",
            "email", "test@feishu.cn",
            "name", "Test User",
            "avatar_url", "https://example.com/avatar.jpg",
            "email_verified", true
        );
        DefaultOAuth2User user = new DefaultOAuth2User(
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attrs,
            "open_id"
        );
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);

        // When
        OAuthClaims claims = extractor.extract(request, user);

        // Then
        assertThat(claims.provider()).isEqualTo("feishu");
        assertThat(claims.subject()).isEqualTo("on_123456"); // union_id preferred
        assertThat(claims.email()).isEqualTo("test@feishu.cn");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.providerLogin()).isEqualTo("Test User");
        assertThat(claims.extra()).containsEntry("open_id", "ou_123456");
        assertThat(claims.extra()).containsEntry("union_id", "on_123456");
    }

    @Test
    void extract_onlyUnionId_usesUnionIdAsSubject() {
        // Given: OAuth2User with only union_id (no open_id)
        Map<String, Object> attrs = Map.of(
            "union_id", "on_789",
            "user_id", "789",
            "email", "user@company.com",
            "name", "Only Union ID",
            "email_verified", true
        );
        DefaultOAuth2User user = new DefaultOAuth2User(
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attrs,
            "union_id"
        );
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);

        // When
        OAuthClaims claims = extractor.extract(request, user);

        // Then
        assertThat(claims.subject()).isEqualTo("on_789");
    }

    @Test
    void extract_noOpenIdNoUnionId_usesUserId() {
        // Given: OAuth2User with only user_id (no open_id, no union_id)
        Map<String, Object> attrs = Map.of(
            "user_id", "uid_999",
            "email", "fallback@test.com",
            "name", "Fallback User",
            "email_verified", false
        );
        DefaultOAuth2User user = new DefaultOAuth2User(
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attrs,
            "user_id"
        );
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);

        // When
        OAuthClaims claims = extractor.extract(request, user);

        // Then
        assertThat(claims.subject()).isEqualTo("uid_999");
    }

    @Test
    void extract_nullEmail_returnsNullEmail() {
        // Given: OAuth2User with no email
        Map<String, Object> attrs = Map.of(
            "open_id", "ou_noemail",
            "union_id", "on_noemail",
            "user_id", "noemail",
            "name", "No Email User"
        );
        DefaultOAuth2User user = new DefaultOAuth2User(
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attrs,
            "open_id"
        );
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);

        // When
        OAuthClaims claims = extractor.extract(request, user);

        // Then
        assertThat(claims.email()).isNull();
    }

    @Test
    void extract_extraContainsAllIdentifiers() {
        // Given
        Map<String, Object> attrs = Map.of(
            "open_id", "ou_extra",
            "union_id", "on_extra",
            "user_id", "extra_uid",
            "email", "extra@test.com",
            "name", "Extra User",
            "avatar_url", "https://example.com/extra.jpg",
            "email_verified", true
        );
        DefaultOAuth2User user = new DefaultOAuth2User(
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attrs,
            "open_id"
        );
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);

        // When
        OAuthClaims claims = extractor.extract(request, user);

        // Then
        assertThat(claims.extra()).containsEntry("open_id", "ou_extra");
        assertThat(claims.extra()).containsEntry("union_id", "on_extra");
        assertThat(claims.extra()).containsEntry("user_id", "extra_uid");
        assertThat(claims.extra()).containsEntry("name", "Extra User");
        assertThat(claims.extra()).containsEntry("avatar_url", "https://example.com/extra.jpg");
    }
}
