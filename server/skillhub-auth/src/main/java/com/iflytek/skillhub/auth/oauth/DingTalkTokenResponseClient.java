package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate = new RestTemplate();

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
                String accessToken = json.get("accessToken").asText();
                if (accessToken == null || accessToken.isEmpty()) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("token_response_missing_field",
                                    "DingTalk token response missing accessToken", null));
                }
                return OAuth2AccessTokenResponse.withToken(accessToken)
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .additionalParameters(Collections.singletonMap("raw_response", response.getBody()))
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