package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FeishuOAuth2AccessTokenResponseClientTest {

    @Test
    void getTokenResponse_postsFeishuJsonRequestAndParsesTokenResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8"))
                .andExpect(content().json("""
                        {
                          "grant_type": "authorization_code",
                          "client_id": "cli_test",
                          "client_secret": "secret_test",
                          "code": "auth-code",
                          "redirect_uri": "https://skillhub.example.com/login/oauth2/code/feishu"
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "access_token": "access-token",
                          "token_type": "Bearer",
                          "expires_in": 7200,
                          "refresh_token": "refresh-token",
                          "scope": "contact:user.base:readonly offline_access"
                        }
                        """, MediaType.APPLICATION_JSON));

        FeishuOAuth2AccessTokenResponseClient client = new FeishuOAuth2AccessTokenResponseClient(builder);

        var response = client.getTokenResponse(grantRequest(false));

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("access-token");
        assertThat(response.getAccessToken().getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
        assertThat(response.getAccessToken().getScopes())
                .containsExactlyInAnyOrder("contact:user.base:readonly", "offline_access");
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
        assertThat(response.getAccessToken().getExpiresAt()).isAfter(Instant.now());
        server.verify();
    }

    @Test
    void getTokenResponse_usesV2EndpointWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8"))
                .andRespond(withSuccess("{\"code\":0,\"access_token\":\"v2-access-token\","
                        + "\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        FeishuOAuth2AccessTokenResponseClient client = new FeishuOAuth2AccessTokenResponseClient(
                builder, request -> OAuth2AccessTokenResponse.withToken("unused").build(), "v2");

        assertThat(client.getTokenResponse(grantRequest(false)).getAccessToken().getTokenValue())
                .isEqualTo("v2-access-token");
        server.verify();
    }

    @Test
    void constructorRejectsUnsupportedProtocolVersion() {
        assertThatThrownBy(() -> new FeishuOAuth2AccessTokenResponseClient(
                RestClient.builder(), request -> OAuth2AccessTokenResponse.withToken("unused").build(), "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v2 or v3");
    }

    @Test
    void getTokenResponse_forwardsCodeVerifierWhenAuthorizationRequestContainsIt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andExpect(content().json("""
                        {
                          "grant_type": "authorization_code",
                          "client_id": "cli_test",
                          "client_secret": "secret_test",
                          "code": "auth-code",
                          "redirect_uri": "https://skillhub.example.com/login/oauth2/code/feishu",
                          "code_verifier": "verifier-value"
                        }
                        """, false))
                .andRespond(withSuccess("{\"code\":0,\"access_token\":\"access-token\","
                        + "\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        FeishuOAuth2AccessTokenResponseClient client = new FeishuOAuth2AccessTokenResponseClient(builder);

        client.getTokenResponse(grantRequest(true));

        server.verify();
    }

    @Test
    void getTokenResponse_rejectsFeishuBusinessErrorReturnedAsHttp200() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withSuccess("""
                        {"code": 20003, "error": "invalid_grant", "error_description": "secret_test rejected auth-code"}
                        """, MediaType.APPLICATION_JSON));

        FeishuOAuth2AccessTokenResponseClient client = new FeishuOAuth2AccessTokenResponseClient(builder);

        assertThatThrownBy(() -> client.getTokenResponse(grantRequest(false)))
                .isInstanceOf(OAuth2AuthorizationException.class)
                .satisfies(error -> {
                    var oauthError = ((OAuth2AuthorizationException) error).getError();
                    assertThat(oauthError.getErrorCode()).isEqualTo("feishu_invalid_token_response");
                    assertThat(oauthError.getDescription()).doesNotContain("secret_test", "auth-code", "rejected");
                });
        server.verify();
    }

    @Test
    void getTokenResponse_rejectsInvalidSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withSuccess("{\"code\":0,\"access_token\":\"access-token\","
                        + "\"token_type\":\"mac\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        FeishuOAuth2AccessTokenResponseClient client = new FeishuOAuth2AccessTokenResponseClient(builder);

        assertThatThrownBy(() -> client.getTokenResponse(grantRequest(false)))
                .isInstanceOf(OAuth2AuthorizationException.class)
                .satisfies(error -> assertThat(((OAuth2AuthorizationException) error).getError().getDescription())
                        .contains("unsupported token type"));
        server.verify();
    }

    @Test
    void getTokenResponse_rejectsHttpErrorWithoutExposingResponseDetails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("client_secret=secret_test"));

        FeishuOAuth2AccessTokenResponseClient client = new FeishuOAuth2AccessTokenResponseClient(builder);

        assertThatThrownBy(() -> client.getTokenResponse(grantRequest(false)))
                .isInstanceOf(OAuth2AuthorizationException.class)
                .satisfies(error -> assertThat(((OAuth2AuthorizationException) error).getError().getDescription())
                        .doesNotContain("secret_test", "auth-code"));
        server.verify();
    }

    @Test
    void getTokenResponse_delegatesNonFeishuRegistrationToStandardClient() {
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> delegate = request ->
                org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse.withToken("github-token")
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .build();
        FeishuOAuth2AccessTokenResponseClient client = new FeishuOAuth2AccessTokenResponseClient(
                RestClient.builder(), delegate);

        var response = client.getTokenResponse(grantRequest("github", false));

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("github-token");
    }

    private OAuth2AuthorizationCodeGrantRequest grantRequest(boolean withCodeVerifier) {
        return grantRequest("feishu", withCodeVerifier);
    }

    private OAuth2AuthorizationCodeGrantRequest grantRequest(String registrationId, boolean withCodeVerifier) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("cli_test")
                .clientSecret("secret_test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
                .tokenUri("https://accounts.feishu.cn/oauth/v3/token")
                .userInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .userNameAttributeName("open_id")
                .clientName("飞书")
                .build();
        OAuth2AuthorizationRequest.Builder request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId())
                .redirectUri("https://skillhub.example.com/login/oauth2/code/feishu")
                .state("state")
                .attributes(attributes -> {
                    if (withCodeVerifier) {
                        attributes.put("code_verifier", "verifier-value");
                    }
                });
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success("auth-code")
                .redirectUri("https://skillhub.example.com/login/oauth2/code/feishu")
                .state("state")
                .build();
        return new OAuth2AuthorizationCodeGrantRequest(
                registration,
                new OAuth2AuthorizationExchange(request.build(), response));
    }
}
