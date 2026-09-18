package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Provider-aware authorization-code token client. Feishu's token endpoint accepts a JSON request
 * and returns business errors in a HTTP-200 response, unlike the form-based OAuth client used by
 * the other providers.
 */
@Component
public class FeishuOAuth2AccessTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final String FEISHU_PROVIDER = "feishu";
    private static final String V2 = "v2";
    private static final String V3 = "v3";
    private static final String DEFAULT_V2_TOKEN_URI = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";
    private static final String DEFAULT_V3_TOKEN_URI = "https://accounts.feishu.cn/oauth/v3/token";
    private static final String INVALID_TOKEN_RESPONSE = "feishu_invalid_token_response";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> standardClient;
    private final String protocolVersion;

    @Autowired
    public FeishuOAuth2AccessTokenResponseClient(
            @Value("${OAUTH2_FEISHU_PROTOCOL_VERSION:v3}") String protocolVersion) {
        this(RestClient.builder().requestFactory(defaultRequestFactory()),
                new DefaultAuthorizationCodeTokenResponseClient(), protocolVersion);
    }

    FeishuOAuth2AccessTokenResponseClient(
            RestClient.Builder restClientBuilder) {
        this(restClientBuilder, new DefaultAuthorizationCodeTokenResponseClient(), V3);
    }

    FeishuOAuth2AccessTokenResponseClient(
            RestClient.Builder restClientBuilder,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> standardClient) {
        this(restClientBuilder, standardClient, V3);
    }

    FeishuOAuth2AccessTokenResponseClient(
            RestClient.Builder restClientBuilder,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> standardClient,
            String protocolVersion) {
        this.restClient = restClientBuilder.build();
        this.standardClient = standardClient;
        this.protocolVersion = normalizeProtocolVersion(protocolVersion);
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest) {
        if (!FEISHU_PROVIDER.equals(authorizationCodeGrantRequest.getClientRegistration().getRegistrationId())) {
            return standardClient.getTokenResponse(authorizationCodeGrantRequest);
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("grant_type", "authorization_code");
        requestBody.put("client_id", authorizationCodeGrantRequest.getClientRegistration().getClientId());
        requestBody.put("client_secret", authorizationCodeGrantRequest.getClientRegistration().getClientSecret());
        requestBody.put("code", authorizationCodeGrantRequest.getAuthorizationExchange()
                .getAuthorizationResponse().getCode());

        String redirectUri = authorizationCodeGrantRequest.getAuthorizationExchange()
                .getAuthorizationRequest().getRedirectUri();
        if (redirectUri != null && !redirectUri.isBlank()) {
            requestBody.put("redirect_uri", redirectUri);
        }
        Object codeVerifier = authorizationCodeGrantRequest.getAuthorizationExchange()
                .getAuthorizationRequest().getAttribute("code_verifier");
        if (codeVerifier instanceof String verifier && !verifier.isBlank()) {
            requestBody.put("code_verifier", verifier);
        }

        try {
            return restClient.post()
                    .uri(tokenUri(authorizationCodeGrantRequest))
                    .contentType(MediaType.parseMediaType("application/json; charset=utf-8"))
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(requestBody)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw tokenError("Feishu token endpoint returned HTTP "
                                    + response.getStatusCode().value());
                        }
                        return parseResponse(readBounded(response.getBody()));
                    });
        } catch (OAuth2AuthorizationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw tokenError("Feishu token exchange failed", exception);
        }
    }

    private static OAuth2AccessTokenResponse parseResponse(byte[] responseBytes) {
        try {
            JsonNode response = OBJECT_MAPPER.readTree(responseBytes);
            int code = response.path("code").asInt(-1);
            if (code != 0) {
                throw tokenError("Feishu token endpoint returned business error code " + code);
            }

            String accessToken = text(response, "access_token");
            if (accessToken == null) {
                throw tokenError("Feishu token endpoint returned no access token");
            }

            String tokenType = text(response, "token_type");
            if (tokenType != null && !"Bearer".equalsIgnoreCase(tokenType)) {
                throw tokenError("Feishu token endpoint returned unsupported token type");
            }
            long expiresIn = response.path("expires_in").asLong(-1);
            if (expiresIn <= 0) {
                throw tokenError("Feishu token endpoint returned invalid expires_in");
            }

            OAuth2AccessTokenResponse.Builder tokenResponse = OAuth2AccessTokenResponse
                    .withToken(accessToken)
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(expiresIn);
            String refreshToken = text(response, "refresh_token");
            if (refreshToken != null) {
                tokenResponse.refreshToken(refreshToken);
            }
            String scope = text(response, "scope");
            if (scope != null) {
                tokenResponse.scopes(Set.of(scope.trim().split("\\s+")));
            }
            return tokenResponse.build();
        } catch (OAuth2AuthorizationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw tokenError("Feishu token endpoint returned an invalid response", exception);
        }
    }

    private String tokenUri(OAuth2AuthorizationCodeGrantRequest request) {
        String configuredUri = request.getClientRegistration().getProviderDetails().getTokenUri();
        if (V2.equals(protocolVersion) && DEFAULT_V3_TOKEN_URI.equals(configuredUri)) {
            return DEFAULT_V2_TOKEN_URI;
        }
        if (V3.equals(protocolVersion) && DEFAULT_V2_TOKEN_URI.equals(configuredUri)) {
            return DEFAULT_V3_TOKEN_URI;
        }
        return configuredUri;
    }

    private static String normalizeProtocolVersion(String value) {
        String normalized = value == null ? V3 : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!V2.equals(normalized) && !V3.equals(normalized)) {
            throw new IllegalArgumentException(
                    "OAUTH2_FEISHU_PROTOCOL_VERSION must be either v2 or v3");
        }
        return normalized;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue()
                : null;
    }

    private static ClientHttpRequestFactory defaultRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    private static byte[] readBounded(InputStream body) throws IOException {
        if (body == null) {
            throw new IOException("empty response body");
        }
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new IOException("response body exceeds configured limit");
        }
        return bytes;
    }

    private static OAuth2AuthorizationException tokenError(String description) {
        return tokenError(description, null);
    }

    private static OAuth2AuthorizationException tokenError(String description, Throwable cause) {
        OAuth2Error error = new OAuth2Error(INVALID_TOKEN_RESPONSE, description, null);
        return cause == null ? new OAuth2AuthorizationException(error) : new OAuth2AuthorizationException(error, cause);
    }
}
