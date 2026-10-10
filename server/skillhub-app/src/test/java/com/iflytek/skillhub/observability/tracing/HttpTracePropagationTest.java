package com.iflytek.skillhub.observability.tracing;

import com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.config.SkillScannerConfig;
import com.iflytek.skillhub.config.SkillScannerProperties;
import com.iflytek.skillhub.infra.http.HttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTracePropagationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.flyway.enabled=false",
                    "spring.jpa.hibernate.ddl-auto=none",
                    "skillhub.observability.tracing-mode=otel-sdk",
                    "management.tracing.sampling.probability=1.0",
                    "skillhub.security.scanner.enabled=true"
            );

    @Test
    void shouldPropagateW3cContextToScannerButNotExternalGitLab() throws Exception {
        try (HeaderCaptureServer scannerServer =
                     new HeaderCaptureServer("text/plain", "scanner-ok");
             HeaderCaptureServer gitLabServer =
                     new HeaderCaptureServer(
                             "application/json",
                             """
                             [
                               {
                                 "email": "alice@gitlab.example",
                                 "confirmed_at": "2026-04-16T08:00:00Z"
                               }
                             ]
                             """
                     )) {
            contextRunner.run(context -> {
                Tracer tracer = context.getBean(Tracer.class);
                HttpClient scannerClient =
                        context.getBean("scannerHttpClient", HttpClient.class);
                GitLabClaimsExtractor gitLabClaimsExtractor =
                        context.getBean(GitLabClaimsExtractor.class);
                Span span = tracer.nextSpan().name("outbound-boundary").start();
                try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                    assertThat(scannerClient.get(
                            scannerServer.url("/health"),
                            String.class
                    )).isEqualTo("scanner-ok");

                    OAuthClaims claims = gitLabClaimsExtractor.extract(
                            gitLabRequest(gitLabServer.url("/api/v4/user")),
                            new DefaultOAuth2User(
                                    List.of(),
                                    Map.of(
                                            "id", 42,
                                            "username", "alice",
                                            "email", "alice+pending@gitlab.example"
                                    ),
                                    "username"
                            )
                    );
                    assertThat(claims.email())
                            .isEqualTo("alice@gitlab.example");
                } finally {
                    span.end();
                }

                String scannerTraceparent = scannerServer.traceparent();
                assertThat(scannerTraceparent)
                        .matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$");
                assertThat(scannerTraceparent.substring(3, 35))
                        .isEqualTo(span.context().traceId());
                assertThat(gitLabServer.traceparent()).isNull();
            });
        }
    }

    private OAuth2UserRequest gitLabRequest(String userInfoUri) {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId("gitlab")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read_user", "email")
                .authorizationUri("https://gitlab.example/oauth/authorize")
                .tokenUri("https://gitlab.example/oauth/token")
                .userInfoUri(userInfoUri)
                .userNameAttributeName("username")
                .clientName("GitLab")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }

    private static final class HeaderCaptureServer implements AutoCloseable {

        private final HttpServer server;
        private final AtomicReference<String> traceparent = new AtomicReference<>();

        private HeaderCaptureServer(String contentType, String body) throws IOException {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(
                    exchange,
                    contentType,
                    response
            ));
            server.start();
        }

        private void respond(
                HttpExchange exchange,
                String contentType,
                byte[] response
        ) throws IOException {
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, response.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private String traceparent() {
            return traceparent.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({
            SkillHubTracingConfiguration.class,
            SkillScannerConfig.class,
            SkillScannerProperties.class,
            GitLabClaimsExtractor.class
    })
    static class TestApplication {
    }
}
