package com.iflytek.skillhub.auth.oauth;

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
 * Loads DingTalk (钉钉) user info, which deviates from standard OAuth: the access token travels in
 * a custom {@code x-acs-dingtalk-access-token} header rather than {@code Authorization: Bearer}.
 *
 * <p>This service only fetches attributes. Account matching, provisioning and session creation
 * stay with the unified identity core reached through {@link OAuthLoginFlowService}, so DingTalk
 * cannot decide who a login resolves to.
 */
@Component
public class DingTalkOAuth2UserService implements ProviderOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkOAuth2UserService.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /** A DingTalk contact payload is well under 1 KB; this only needs to stop an unbounded body. */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    /**
     * Uses an external-service client that is intentionally not customized with application
     * tracing. Trace context must not be propagated to the external DingTalk service.
     */
    @Autowired
    public DingTalkOAuth2UserService() {
        this(RestClient.builder().requestFactory(defaultRequestFactory()));
    }

    public DingTalkOAuth2UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Bounds the userinfo call so an unresponsive DingTalk endpoint cannot hold a login thread. The
     * timeouts apply to this provider client only and do not change the shared HTTP defaults.
     */
    private static ClientHttpRequestFactory defaultRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public String getProvider() {
        return DingTalkOAuth2Constants.REGISTRATION_ID;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String userInfoUri = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUri();

        Map<String, Object> payload;
        try {
            payload = restClient.get()
                    .uri(userInfoUri)
                    .header(
                            DingTalkOAuth2Constants.ACCESS_TOKEN_HEADER,
                            userRequest.getAccessToken().getTokenValue()
                    )
                    .exchange((request, clientResponse) -> readBounded(clientResponse.getBody()));
        } catch (Exception e) {
            // Exception class only: the message can quote the request URI, which holds the token.
            log.warn("DingTalk user info request failed with {}", e.getClass().getSimpleName());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("dingtalk_userinfo_error", "Failed to load DingTalk user info", null),
                    e
            );
        }

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                normalize(payload),
                DingTalkOAuth2Constants.SUBJECT_CLAIM_NAME
        );
    }

    /**
     * Reads at most {@link #MAX_RESPONSE_BYTES} before parsing, so a misconfigured or hostile
     * {@code OAUTH2_DINGTALK_BASE_URI} cannot stream an unbounded body into the parser. Reading one
     * byte past the cap is what distinguishes an oversized payload from one that exactly fills it.
     */
    private static Map<String, Object> readBounded(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new IOException("DingTalk user info response exceeds " + MAX_RESPONSE_BYTES + " bytes");
        }
        return OBJECT_MAPPER.readValue(bytes, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    }

    /**
     * Copies through only the attributes the platform consumes, and aliases DingTalk's
     * {@code avatarUrl} to the {@code avatar_url} key the identity core reads. Attributes the
     * platform does not use -- notably {@code mobile} and {@code stateCode} -- are dropped rather
     * than carried into the principal, keeping unused PII out of claims and logs.
     */
    private static Map<String, Object> normalize(Map<String, Object> payload) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        copyIfPresent(attributes, payload, DingTalkOAuth2Constants.SUBJECT_CLAIM_NAME);
        copyIfPresent(attributes, payload, "nick");
        copyIfPresent(attributes, payload, "name");
        copyIfPresent(attributes, payload, "email");
        Object avatar = payload.get("avatarUrl");
        if (avatar != null && !String.valueOf(avatar).isBlank()) {
            attributes.put("avatar_url", avatar);
        }
        if (!attributes.containsKey(DingTalkOAuth2Constants.SUBJECT_CLAIM_NAME)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "dingtalk_userinfo_error",
                            "DingTalk user info missing " + DingTalkOAuth2Constants.SUBJECT_CLAIM_NAME,
                            null
                    )
            );
        }
        return attributes;
    }

    private static void copyIfPresent(
            Map<String, Object> target,
            Map<String, Object> source,
            String key
    ) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }
}
