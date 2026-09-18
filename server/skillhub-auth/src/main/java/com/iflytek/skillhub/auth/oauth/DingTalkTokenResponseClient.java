package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Custom token response client for DingTalk (钉钉).
 *
 * <p>DingTalk requires a JSON body for token exchange instead of the standard
 * form-urlencoded format. This client adapts the request accordingly.
 *
 * <p>Request body format:
 * <pre>{ "clientId": "...", "clientSecret": "...", "code": "...", "grantType": "authorization_code" }</pre>
 */
@Component
public class DingTalkTokenResponseClient implements ProviderTokenResponseClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate;

    @Autowired
    public DingTalkTokenResponseClient() {
        this.restTemplate = buildRestTemplate();
    }

    /** Package-visible constructor for unit testing with a mock RestTemplate. */
    DingTalkTokenResponseClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProvider() {
        return DingTalkOAuth2Constants.REGISTRATION_ID;
    }

    /** A DingTalk token payload is a few hundred bytes; this only stops an unbounded body. */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    /** Package-visible so a test can exercise the production template, size cap included. */
    static RestTemplate buildRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestTemplate template = new RestTemplate(factory);
        // The timeouts bound how long the exchange may take; this bounds how much it may return, so
        // a misconfigured or hostile token endpoint cannot stream an unbounded body into the parser.
        // The userinfo client applies the same cap.
        template.getInterceptors().add((request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new IOException("DingTalk token response exceeds " + MAX_RESPONSE_BYTES + " bytes");
            }
            return new BoundedClientHttpResponse(response, bytes);
        });
        return template;
    }

    /** Replays the already-read, size-checked body so the converters can still parse it. */
    private record BoundedClientHttpResponse(ClientHttpResponse delegate, byte[] body)
            implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest)
            throws OAuth2AuthenticationException {
        String tokenUri = authorizationCodeGrantRequest.getClientRegistration().getProviderDetails().getTokenUri();
        String clientId = authorizationCodeGrantRequest.getClientRegistration().getClientId();
        String clientSecret = authorizationCodeGrantRequest.getClientRegistration().getClientSecret();
        String code = authorizationCodeGrantRequest.getAuthorizationExchange()
                .getAuthorizationResponse()
                .getCode();

        Map<String, Object> tokenRequest = Map.of(
                "clientId", clientId,
                "clientSecret", clientSecret,
                "code", code,
                "grantType", "authorization_code"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(tokenUri, new HttpEntity<>(tokenRequest, headers), String.class);
        } catch (RestClientResponseException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("token_exchange_io_error",
                            "DingTalk token exchange failed with HTTP " + e.getStatusCode().value(), null));
        } catch (RestClientException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("token_exchange_io_error",
                            "DingTalk token exchange request failed", null));
        }

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode json = MAPPER.readTree(response.getBody());

                JsonNode accessTokenNode = json.get("accessToken");
                if (accessTokenNode == null || accessTokenNode.isNull()) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("token_response_missing_field",
                                    "DingTalk token response missing accessToken field", null));
                }
                String accessToken = accessTokenNode.asText();
                if (accessToken.isBlank()) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("token_response_missing_field",
                                    "DingTalk token response has empty accessToken", null));
                }

                JsonNode expireInNode = json.get("expireIn");
                if (expireInNode == null || !expireInNode.isIntegralNumber() || !expireInNode.canConvertToLong()) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("token_response_invalid_expiry",
                                    "DingTalk token response has invalid expireIn field", null));
                }
                long expireInSeconds = expireInNode.longValue();
                if (expireInSeconds <= 0) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("token_response_invalid_expiry",
                                    "DingTalk token response has non-positive expireIn field", null));
                }

                // Only include non-sensitive fields in additional parameters.
                Map<String, Object> safeParams = Map.of("expireIn", expireInSeconds);

                return OAuth2AccessTokenResponse.withToken(accessToken)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .expiresIn(expireInSeconds)
                        .additionalParameters(safeParams)
                        .build();
            } catch (OAuth2AuthenticationException e) {
                throw e;
            } catch (Exception e) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("token_parse_error",
                                "Failed to parse DingTalk token response", null));
            }
        }

        throw new OAuth2AuthenticationException(
                new OAuth2Error("token_exchange_failed",
                        "DingTalk token exchange failed: HTTP " + response.getStatusCode(), null));
    }
}
