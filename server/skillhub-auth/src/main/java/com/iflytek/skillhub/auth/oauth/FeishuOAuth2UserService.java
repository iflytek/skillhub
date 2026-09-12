package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Loads Feishu (Lark) user info, which deviates from the standard OAuth format: the response is
 * wrapped in a {@code {code, msg, data}} envelope and errors are reported with HTTP 200.
 */
@Component
public class FeishuOAuth2UserService implements ProviderOAuth2UserService {

    static final String PROVIDER = "feishu";

    private final RestClient restClient;

    /**
     * Uses an external-service client that is intentionally not customized with application
     * tracing. Trace context must not be propagated to the external Feishu service.
     */
    @Autowired
    public FeishuOAuth2UserService() {
        this(RestClient.builder());
    }

    public FeishuOAuth2UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String userInfoUri = userRequest.getClientRegistration().getProviderDetails()
            .getUserInfoEndpoint().getUri();

        FeishuUserResponse response;
        try {
            response = restClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userRequest.getAccessToken().getTokenValue())
                .retrieve()
                .body(new ParameterizedTypeReference<FeishuUserResponse>() {});
        } catch (Exception e) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("feishu_userinfo_error", "Failed to load Feishu user info: " + e.getMessage(), null),
                e
            );
        }

        if (response == null || response.code() != 0 || response.data() == null) {
            String msg = response != null ? response.msg() : "empty response";
            throw new OAuth2AuthenticationException(
                new OAuth2Error("feishu_userinfo_error", "Feishu user info error: " + msg, null)
            );
        }

        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
            .getUserInfoEndpoint().getUserNameAttributeName();

        Map<String, Object> attributes = flatten(response.data(), userNameAttributeName);
        return new DefaultOAuth2User(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
            attributes,
            userNameAttributeName
        );
    }

    private Map<String, Object> flatten(FeishuUserData data, String userNameAttributeName) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "open_id", data.openId());
        putIfPresent(attributes, "union_id", data.unionId());
        putIfPresent(attributes, "name", data.name());
        putIfPresent(attributes, "en_name", data.enName());
        putIfPresent(attributes, "avatar_url", data.avatarUrl());
        putIfPresent(attributes, "email", data.email());
        putIfPresent(attributes, "enterprise_email", data.enterpriseEmail());
        putIfPresent(attributes, "mobile", data.mobile());
        if (!attributes.containsKey(userNameAttributeName)) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("feishu_userinfo_error", "Feishu user info missing " + userNameAttributeName, null)
            );
        }
        return attributes;
    }

    private void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeishuUserResponse(int code, String msg, @JsonProperty("data") FeishuUserData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeishuUserData(
        @JsonProperty("open_id") String openId,
        @JsonProperty("union_id") String unionId,
        @JsonProperty("name") String name,
        @JsonProperty("en_name") String enName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("email") String email,
        @JsonProperty("enterprise_email") String enterpriseEmail,
        @JsonProperty("mobile") String mobile
    ) {}
}
