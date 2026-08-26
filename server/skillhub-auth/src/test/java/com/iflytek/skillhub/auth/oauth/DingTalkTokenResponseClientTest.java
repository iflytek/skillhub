package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DingTalkTokenResponseClientTest {

    private DingTalkTokenResponseClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new DingTalkTokenResponseClient(restTemplate);
    }

    @Test
    void getTokenResponse_returnsAccessTokenOnSuccess() {
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andRespond(withSuccess(
                        """
                        {
                          "accessToken": "dt_access_token_123",
                          "expireIn": 7200
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OAuth2AccessTokenResponse response = client.getTokenResponse(authorizationCodeGrantRequest());

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("dt_access_token_123");
        assertThat(response.getAccessToken().getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
        assertThat(response.getAccessToken().getIssuedAt()).isNotNull();
        assertThat(response.getAccessToken().getExpiresAt()).isNotNull();
        assertThat(Duration.between(
                response.getAccessToken().getIssuedAt(),
                response.getAccessToken().getExpiresAt())).isEqualTo(Duration.ofSeconds(7200));
        assertThat(response.getAdditionalParameters().get("expireIn")).isEqualTo(7200L);
        // Verify raw_response is NOT included (sensitive data leak fix)
        assertThat(response.getAdditionalParameters().containsKey("raw_response")).isFalse();
        mockServer.verify();
    }

    @Test
    void getTokenResponse_throwsWhenAccessTokenFieldMissing() {
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andRespond(withSuccess(
                        """
                        {
                          "expireIn": 7200
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.getTokenResponse(authorizationCodeGrantRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode()).isEqualTo("token_response_missing_field"));
    }

    @Test
    void getTokenResponse_throwsWhenAccessTokenIsNull() {
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andRespond(withSuccess(
                        """
                        {
                          "accessToken": null,
                          "expireIn": 7200
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.getTokenResponse(authorizationCodeGrantRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode()).isEqualTo("token_response_missing_field"));
    }

    @Test
    void getTokenResponse_throwsWhenAccessTokenIsEmpty() {
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andRespond(withSuccess(
                        """
                        {
                          "accessToken": "",
                          "expireIn": 7200
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.getTokenResponse(authorizationCodeGrantRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode()).isEqualTo("token_response_missing_field"));
    }

    @Test
    void getTokenResponse_throwsOnHttpError() {
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andRespond(withServerError().body("sensitive-upstream-response"));

        assertThatThrownBy(() -> client.getTokenResponse(authorizationCodeGrantRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException oauthException = (OAuth2AuthenticationException) ex;
                    assertThat(oauthException.getError().getErrorCode()).isEqualTo("token_exchange_io_error");
                    assertThat(oauthException.getMessage()).doesNotContain("sensitive-upstream-response");
                });
    }

    @Test
    void getTokenResponse_throwsWhenExpireInIsMissing() {
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andRespond(withSuccess(
                        """
                        {
                          "accessToken": "dt_access_token_123"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.getTokenResponse(authorizationCodeGrantRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex)
                        .getError().getErrorCode()).isEqualTo("token_response_invalid_expiry"));
    }

    @Test
    void getTokenResponse_throwsWhenExpireInIsNonPositive() {
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andRespond(withSuccess(
                        """
                        {
                          "accessToken": "dt_access_token_123",
                          "expireIn": 0
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.getTokenResponse(authorizationCodeGrantRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex)
                        .getError().getErrorCode()).isEqualTo("token_response_invalid_expiry"));
    }

    private OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("dingtalk")
                .clientId("dingzgzf3b9k7jv74iq2")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .userInfoUri("https://api.dingtalk.com/v1.0/contact/users/me")
                .userNameAttributeName("unionId")
                .clientName("钉钉")
                .build();

        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId(registration.getClientId())
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .redirectUri(registration.getRedirectUri())
                .scopes(registration.getScopes())
                .state("test-state")
                .build();

        OAuth2AuthorizationResponse authResponse = OAuth2AuthorizationResponse.success("test-code")
                .redirectUri(registration.getRedirectUri())
                .state("test-state")
                .build();

        return new OAuth2AuthorizationCodeGrantRequest(
                registration,
                new OAuth2AuthorizationExchange(authRequest, authResponse)
        );
    }
}
