package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubClaimsExtractorTest {

    @Test
    void extract_doesNotTrustProfileEmailWhenEmailsApiHasNoVerifiedEmail() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [{"email":"alice@example.com","primary":true,"verified":false}]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(userRequest(), githubUser("alice@example.com"));

        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
    }

    @Test
    void extract_usesVerifiedEmailFromEmailsApi() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [
                          {"email":"secondary@example.com","primary":false,"verified":true},
                          {"email":"alice@example.com","primary":true,"verified":true}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(userRequest(), githubUser(null));

        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void extract_treatsEmailApiFailureAsUnverified() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.FORBIDDEN));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(userRequest(), githubUser("alice@example.com"));

        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
    }

    @Test
    void extract_usesConfiguredApiBaseUrl() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://github-proxy.internal/user/emails"))
                .andRespond(withSuccess(
                        "[{\"email\":\"alice@example.com\",\"primary\":true,\"verified\":true}]",
                        MediaType.APPLICATION_JSON));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder,
                "https://github-proxy.internal");

        OAuthClaims claims = extractor.extract(userRequest(), githubUser(null));

        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.emailVerified()).isTrue();
        server.verify();
    }

    private DefaultOAuth2User githubUser(String email) {
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("id", 42);
        attributes.put("login", "alice");
        attributes.put("email", email);
        return new DefaultOAuth2User(List.of(), attributes, "login");
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("login")
                .clientName("GitHub")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-123",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
