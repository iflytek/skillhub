package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

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
public class DingTalkTokenResponseClient implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate;

    public DingTalkTokenResponseClient() {
        this.restTemplate = buildRestTemplate();
    }

    /** Package-visible constructor for unit testing with a mock RestTemplate. */
    DingTalkTokenResponseClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static RestTemplate buildRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
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
        } catch (Exception e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("token_exchange_io_error",
                            "Failed to exchange code for DingTalk access token: " + e.getMessage(), null), e);
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
                if (accessToken.isEmpty()) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("token_response_missing_field",
                                    "DingTalk token response has empty accessToken", null));
                }

                // Only include non-sensitive fields in additional parameters
                Map<String, Object> safeParams = new java.util.LinkedHashMap<>();
                JsonNode expireInNode = json.get("expireIn");
                if (expireInNode != null && !expireInNode.isNull()) {
                    safeParams.put("expireIn", expireInNode.asLong());
                }

                return OAuth2AccessTokenResponse.withToken(accessToken)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .additionalParameters(safeParams)
                        .build();
            } catch (OAuth2AuthenticationException e) {
                throw e;
            } catch (Exception e) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("token_parse_error",
                                "Failed to parse DingTalk token response", null), e);
            }
        }

        throw new OAuth2AuthenticationException(
                new OAuth2Error("token_exchange_failed",
                        "DingTalk token exchange failed: HTTP " + response.getStatusCode(), null));
    }
}