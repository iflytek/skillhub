package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FeishuOAuth2UserService.class);

    static final String PROVIDER = "feishu";

    private final RestClient restClient;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** A Feishu user_info payload is well under 1 KB; this only needs to stop an unbounded body. */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Uses an external-service client that is intentionally not customized with application
     * tracing. Trace context must not be propagated to the external Feishu service.
     */
    @Autowired
    public FeishuOAuth2UserService() {
        this(RestClient.builder().requestFactory(defaultRequestFactory()));
    }

    public FeishuOAuth2UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /**
     * Bounds the userinfo call so an unresponsive Feishu endpoint cannot hold a login thread. The
     * timeouts apply to this provider client only and do not change the shared HTTP defaults.
     */
    private static ClientHttpRequestFactory defaultRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * Reads at most {@link #MAX_RESPONSE_BYTES} before parsing, so a misconfigured or hostile
     * {@code OAUTH2_FEISHU_BASE_URI} cannot stream an unbounded body into the parser. Reading one
     * byte past the cap is what distinguishes an oversized payload from one that exactly fills it.
     */
    private static FeishuUserResponse readBounded(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new IOException("Feishu user info response exceeds " + MAX_RESPONSE_BYTES + " bytes");
        }
        return OBJECT_MAPPER.readValue(bytes, FeishuUserResponse.class);
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
                .exchange((request, clientResponse) -> readBounded(clientResponse.getBody()));
        } catch (Exception e) {
            // Exception class only: the message can quote the request URI, which holds the token.
            // Nothing downstream logs this failure, so without this line it would be silent.
            log.warn("Feishu user info request failed with {}", e.getClass().getSimpleName());
            // The cause carries the detail for operators; the OAuth2Error description stays generic
            // for the same reason the log line is.
            throw new OAuth2AuthenticationException(
                new OAuth2Error("feishu_userinfo_error", "Failed to load Feishu user info", null),
                e
            );
        }

        if (response == null || response.code() != 0 || response.data() == null) {
            // Feishu's own error code is safe to record; its msg text is not.
            log.warn(
                "Feishu user info returned error code {}",
                response == null ? "none" : response.code()
            );
            throw new OAuth2AuthenticationException(
                new OAuth2Error(
                    "feishu_userinfo_error",
                    "Feishu user info error, code " + (response == null ? "none" : response.code()),
                    null
                )
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
        @JsonProperty("enterprise_email") String enterpriseEmail
    ) {}
}
