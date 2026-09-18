package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;

class DispatchingTokenResponseClientTest {

    @Test
    void routesToProviderOverrideWhenOneClaimsTheRegistration() {
        OAuth2AccessTokenResponse overrideResponse = response("from-override");
        OAuth2AccessTokenResponse defaultResponse = response("from-default");
        DispatchingTokenResponseClient client = new DispatchingTokenResponseClient(
                List.of(stubProvider("dingtalk", overrideResponse)),
                request -> defaultResponse
        );

        OAuth2AccessTokenResponse result = client.getTokenResponse(grantRequest("dingtalk"));

        assertThat(result.getAccessToken().getTokenValue()).isEqualTo("from-override");
    }

    @Test
    void fallsBackToDefaultClientForUnclaimedRegistrations() {
        OAuth2AccessTokenResponse overrideResponse = response("from-override");
        OAuth2AccessTokenResponse defaultResponse = response("from-default");
        DispatchingTokenResponseClient client = new DispatchingTokenResponseClient(
                List.of(stubProvider("dingtalk", overrideResponse)),
                request -> defaultResponse
        );

        // GitHub must keep the standard exchange even while a DingTalk override is registered.
        OAuth2AccessTokenResponse result = client.getTokenResponse(grantRequest("github"));

        assertThat(result.getAccessToken().getTokenValue()).isEqualTo("from-default");
    }

    private static ProviderTokenResponseClient stubProvider(
            String provider,
            OAuth2AccessTokenResponse response
    ) {
        return new ProviderTokenResponseClient() {
            @Override
            public String getProvider() {
                return provider;
            }

            @Override
            public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest request) {
                return response;
            }
        };
    }

    private static OAuth2AccessTokenResponse response(String tokenValue) {
        return OAuth2AccessTokenResponse.withToken(tokenValue)
                .tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(3600)
                .build();
    }

    private static OAuth2AuthorizationCodeGrantRequest grantRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .redirectUri("https://skillhub.example/login/oauth2/code/" + registrationId)
                .authorizationUri("https://provider.example/authorize")
                .tokenUri("https://provider.example/token")
                .userInfoUri("https://provider.example/me")
                .userNameAttributeName("id")
                .build();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example/authorize")
                .clientId("client")
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
