package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Feishu (Lark) OAuth2 claims extractor.
 *
 * Feishu OAuth2 flow:
 * 1. Authorize: https://accounts.feishu.cn/open-apis/authen/v1/authorize
 * 2. Get access token: https://open.feishu.cn/open-apis/authen/v2/oauth/token
 * 3. Get user info: https://open.feishu.cn/open-apis/authen/v1/user_info
 */
@Component
public class FeishuClaimsExtractor implements OAuthClaimsExtractor {

    private static final String BASE_URL = "https://open.feishu.cn";
    private static final String USER_INFO_PATH = "/open-apis/authen/v1/user_info";

    private final RestClient restClient = RestClient.builder()
        .baseUrl(BASE_URL)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    @Override
    public String getProvider() {
        return "feishu";
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        
        // Try to get user info directly from the attributes first
        String openId = extractString(attrs, "open_id");
        String unionId = extractString(attrs, "union_id");
        String userId = extractString(attrs, "user_id");
        String email = extractString(attrs, "email");
        String name = extractString(attrs, "name");
        String avatarUrl = extractString(attrs, "avatar_url");
        Boolean emailVerified = extractBoolean(attrs, "email_verified");

        // If open_id is not in attributes, we need to call user info endpoint
        // This can happen with certain OAuth configurations
        if (openId == null || openId.isEmpty()) {
            FeishuUserInfo userInfo = loadUserInfo(request);
            if (userInfo != null) {
                openId = userInfo.openId();
                unionId = userInfo.unionId();
                userId = userInfo.userId();
                email = userInfo.email();
                name = userInfo.name();
                avatarUrl = userInfo.avatarUrl();
                emailVerified = userInfo.emailVerified();
            }
        }

        // Use union_id as the primary subject if available, otherwise open_id
        String subject = (unionId != null && !unionId.isEmpty()) ? unionId : openId;
        if (subject == null || subject.isEmpty()) {
            subject = userId; // Fallback to user_id
        }

        // Feishu emails are typically verified for enterprise accounts
        boolean verified = emailVerified != null && emailVerified;

        // Build extra attributes map
        Map<String, Object> extra = Map.of(
            "open_id", openId != null ? openId : "",
            "union_id", unionId != null ? unionId : "",
            "user_id", userId != null ? userId : "",
            "name", name != null ? name : "",
            "avatar_url", avatarUrl != null ? avatarUrl : ""
        );

        return new OAuthClaims(
            "feishu",
            subject,
            email,
            verified,
            name, // Use name as providerLogin
            extra
        );
    }

    private FeishuUserInfo loadUserInfo(OAuth2UserRequest request) {
        try {
            return restClient.get()
                .uri(USER_INFO_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                .retrieve()
                .body(FeishuUserInfo.class);
        } catch (Exception e) {
            // Log error but don't fail - some attributes may already be in oAuth2User
            return null;
        }
    }

    private String extractString(Map<String, Object> attrs, String key) {
        Object value = attrs.get(key);
        return value != null ? value.toString() : null;
    }

    private Boolean extractBoolean(Map<String, Object> attrs, String key) {
        Object value = attrs.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    /**
     * Feishu user info response structure.
     * See: https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/authen-v1/user_info/get
     */
    private record FeishuUserInfo(
        @JsonProperty("open_id") String openId,
        @JsonProperty("union_id") String unionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("email_verified") Boolean emailVerified
    ) {}
}
