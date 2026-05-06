package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
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

class GitLabClaimsExtractorTest {

    @Test
    void getProvider_returnsGitlab() {
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(RestClient.builder());
        assertThat(extractor.getProvider()).isEqualTo("gitlab");
    }

    @Test
    void extract_marksProfileEmailVerifiedWhenConfirmedAtPresent() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "username", "alice",
                                "email", "alice@gitlab.example",
                                "confirmed_at", "2026-04-16T08:00:00Z"
                        ),
                        "username"
                )
        );

        assertThat(claims.email()).isEqualTo("alice@gitlab.example");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.providerLogin()).isEqualTo("alice");
    }

    @Test
    void extract_loadsConfirmedEmailFromEmailListWhenProfileEmailIsUnconfirmed() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://gitlab.example.com/api/v4/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [
                          {"email":"alice@gitlab.example","confirmed_at":"2026-04-16T08:00:00Z"},
                          {"email":"alice+pending@gitlab.example","confirmed_at":null}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "username", "alice",
                                "email", "alice+pending@gitlab.example"
                        ),
                        "username"
                )
        );

        assertThat(claims.email()).isEqualTo("alice@gitlab.example");
        assertThat(claims.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void extract_whenEmailNull_fetchesConfirmedEmailFromApi() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://gitlab.example.com/api/v4/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [
                          {"email":"alice@gitlab.example","confirmed_at":"2026-04-16T08:00:00Z"}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "username", "alice"
                        ),
                        "username"
                )
        );

        assertThat(claims.email()).isEqualTo("alice@gitlab.example");
        assertThat(claims.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void extract_whenUsernameNull_usesLoginAttribute() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "login", "alice-login",
                                "email", "alice@gitlab.example",
                                "confirmed_at", "2026-04-16T08:00:00Z"
                        ),
                        "login"
                )
        );

        assertThat(claims.providerLogin()).isEqualTo("alice-login");
    }

    @Test
    void extract_whenApiReturnsNullEmails_returnsNullEmail() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://gitlab.example.com/api/v4/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "username", "alice",
                                "email", "alice+pending@gitlab.example"
                        ),
                        "username"
                )
        );

        assertThat(claims.email()).isEqualTo("alice+pending@gitlab.example");
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
    }

    @Test
    void extract_whenApiReturnsEmptyEmails_returnsNullEmail() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://gitlab.example.com/api/v4/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "username", "alice",
                                "email", "alice+pending@gitlab.example"
                        ),
                        "username"
                )
        );

        assertThat(claims.email()).isEqualTo("alice+pending@gitlab.example");
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
    }

    @Test
    void extract_whenNoConfirmedEmails_returnsNullEmail() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://gitlab.example.com/api/v4/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        [
                          {"email":"alice@gitlab.example","confirmed_at":null}
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "username", "alice",
                                "email", "alice+pending@gitlab.example"
                        ),
                        "username"
                )
        );

        assertThat(claims.email()).isEqualTo("alice+pending@gitlab.example");
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
    }

    @Test
    void extract_whenApiThrowsException_returnsNullEmail() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://gitlab.example.com/api/v4/user/emails"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond((r) -> { throw new RuntimeException("connection error"); });
        GitLabClaimsExtractor extractor = new GitLabClaimsExtractor(restClientBuilder);

        OAuthClaims claims = extractor.extract(
                userRequest(),
                new DefaultOAuth2User(
                        java.util.List.of(),
                        Map.of(
                                "id", 42,
                                "username", "alice",
                                "email", "alice+pending@gitlab.example"
                        ),
                        "username"
                )
        );

        assertThat(claims.email()).isEqualTo("alice+pending@gitlab.example");
        assertThat(claims.emailVerified()).isFalse();
        server.verify();
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("gitlab")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read_user", "email")
                .authorizationUri("https://gitlab.example.com/oauth/authorize")
                .tokenUri("https://gitlab.example.com/oauth/token")
                .userInfoUri("https://gitlab.example.com/api/v4/user")
                .userNameAttributeName("username")
                .clientName("GitLab")
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
