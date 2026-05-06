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
    void getProvider_returnsGithub() {
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor();
        assertThat(extractor.getProvider()).isEqualTo("github");
    }

    @Test
    void extract_usesPrimaryVerifiedEmail_whenAvailable() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [
                          {"email":"alice@example.com","primary":true,"verified":true},
                          {"email":"alice+2@example.com","primary":false,"verified":true}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        List.of(),
                        Map.of(
                                "id", 42,
                                "login", "alice",
                                "email", "profile@example.com"
                        ),
                        "login"
                )
        );

        assertThat(claims.provider()).isEqualTo("github");
        assertThat(claims.subject()).isEqualTo("42");
        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.providerLogin()).isEqualTo("alice");
        server.verify();
    }

    @Test
    void extract_fallsBackToProfileEmail_whenNoPrimaryEmail() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        "[]",
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        List.of(),
                        Map.of(
                                "id", 42,
                                "login", "alice",
                                "email", "profile@example.com"
                        ),
                        "login"
                )
        );

        assertThat(claims.email()).isEqualTo("profile@example.com");
        assertThat(claims.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void extract_usesUnverifiedProfileEmail_whenApiReturnsNullEmails() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        "null",
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        List.of(),
                        Map.of(
                                "id", 42,
                                "login", "alice",
                                "email", "profile@example.com"
                        ),
                        "login"
                )
        );

        assertThat(claims.email()).isEqualTo("profile@example.com");
        assertThat(claims.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void extract_emailVerifiedIsFalse_whenNoEmailAnywhere() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        "null",
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        List.of(),
                        Map.of(
                                "id", 42,
                                "login", "alice"
                        ),
                        "login"
                )
        );

        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
    }

    @Test
    void extract_prefersMostPrimaryAmongVerifiedEmails() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [
                          {"email":"secondary@example.com","primary":false,"verified":true},
                          {"email":"primary@example.com","primary":true,"verified":true}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        List.of(),
                        Map.of("id", 1, "login", "user"),
                        "login"
                )
        );

        assertThat(claims.email()).isEqualTo("primary@example.com");
        server.verify();
    }

    @Test
    void extract_skipsUnverifiedEmailsAndFallsBackToNull() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [
                          {"email":"unverified@example.com","primary":true,"verified":false}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitHubClaimsExtractor extractor = new GitHubClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        List.of(),
                        Map.of("id", 1, "login", "user"),
                        "login"
                )
        );

        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
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
