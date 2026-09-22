package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OAuth2TokenResponseClientsTest {

    @Test
    void standardClientParsesNormalJsonTokenResponse() {
        RestTemplate restTemplate = OAuth2TokenResponseClients.standardRestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://provider.example/token"))
                .andRespond(withSuccess(
                        """
                        {
                          "access_token": "access-token",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        DefaultAuthorizationCodeTokenResponseClient client =
                new DefaultAuthorizationCodeTokenResponseClient();
        client.setRestOperations(restTemplate);

        var response = client.getTokenResponse(grantRequest("github"));

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("access-token");
        assertThat(response.getAccessToken().getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
        server.verify();
    }

    @Test
    void standardClientRejectsOversizedTokenResponseWithoutEchoingBody() {
        RestTemplate restTemplate = OAuth2TokenResponseClients.standardRestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        String sensitivePadding = "secret-response-body-".repeat(4 * 1024);
        server.expect(requestTo("https://provider.example/token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + sensitivePadding + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON
                ));
        DefaultAuthorizationCodeTokenResponseClient client =
                new DefaultAuthorizationCodeTokenResponseClient();
        client.setRestOperations(restTemplate);

        assertThatThrownBy(() -> client.getTokenResponse(grantRequest("gitlab")))
                .satisfies(error -> assertThat(error.getMessage())
                        .contains("OAuth2 token response exceeds")
                        .doesNotContain("secret-response-body"));
        server.verify();
    }

    private static OAuth2AuthorizationCodeGrantRequest grantRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .redirectUri("https://skillhub.example/login/oauth2/code/" + registrationId)
                .authorizationUri("https://provider.example/authorize")
                .tokenUri("https://provider.example/token")
                .userInfoUri("https://provider.example/userinfo")
                .userNameAttributeName("id")
                .build();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example/authorize")
                .clientId("client-id")
                .redirectUri("https://skillhub.example/login/oauth2/code/" + registrationId)
                .state("state-1")
                .build();
        OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success("code-1")
                .redirectUri("https://skillhub.example/login/oauth2/code/" + registrationId)
                .state("state-1")
                .build();
        return new OAuth2AuthorizationCodeGrantRequest(
                registration,
                new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse)
        );
    }
}
