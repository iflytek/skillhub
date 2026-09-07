package com.iflytek.skillhub.auth.connection.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultOidcMetadataSourceTest {

    private static final OidcIssuer ISSUER = new OidcIssuer("https://id.example.com/tenant");

    @Test
    void loadsStrictMetadataAndPublicJwks() {
        QueueTransport transport = new QueueTransport(
                metadata(ISSUER.value(), "https://keys.example.com/jwks", "RS256"),
                jwks("signing-key")
        );
        DefaultOidcMetadataSource source = source(transport, publicResolver());

        OidcMetadataBundle bundle = source.load(ISSUER);

        assertThat(bundle.metadata().issuer()).isEqualTo(ISSUER);
        assertThat(bundle.metadata().authorizationEndpoint())
                .isEqualTo(URI.create("https://id.example.com/authorize"));
        assertThat(bundle.metadata().idTokenSigningAlgorithms()).containsExactly("RS256");
        assertThat(bundle.jwkSet().findByKeyId("signing-key").getKeyID())
                .isEqualTo("signing-key");
        assertThat(transport.requests).containsExactly(
                ISSUER.discoveryUri(),
                URI.create("https://keys.example.com/jwks")
        );
    }

    @Test
    void rejectsIssuerMismatchDuplicateFieldsUnsafeAlgorithmsAndPrivateEndpoints() {
        assertUnavailable(() -> source(new QueueTransport(
                metadata("https://attacker.example", "https://keys.example.com/jwks", "RS256")
        ), publicResolver()).load(ISSUER));

        String duplicateIssuer = metadata(ISSUER.value(), "https://keys.example.com/jwks", "RS256")
                .replaceFirst("\\{", "{\"issuer\":\"https://duplicate.example\",");
        assertUnavailable(() -> source(
                new QueueTransport(duplicateIssuer),
                publicResolver()
        ).load(ISSUER));

        assertUnavailable(() -> source(new QueueTransport(
                metadata(ISSUER.value(), "https://keys.example.com/jwks", "none")
        ), publicResolver()).load(ISSUER));

        OidcHostAddressResolver privateKeys = host -> host.equals("keys.example.com")
                ? List.of(InetAddress.getByName("10.1.2.3"))
                : publicResolver().resolve(host);
        assertUnavailable(() -> source(new QueueTransport(
                metadata(ISSUER.value(), "https://keys.example.com/jwks", "RS256")
        ), privateKeys).load(ISSUER));

        assertUnavailable(() -> source(new QueueTransport(
                metadata(ISSUER.value(), "https://keys.example.com/jwks", "RS256"),
                "{\"keys\":[{\"kty\":\"oct\",\"kid\":\"symmetric\",\"k\":\"AQAB\"}]}"
        ), publicResolver()).load(ISSUER));

        assertUnavailable(() -> source(new QueueTransport(
                metadata(ISSUER.value(), "https://keys.example.com/jwks", "RS256"),
                """
                        {"keys":[
                          {"kty":"RSA","kid":"duplicate","n":"AQAB","e":"AQAB"},
                          {"kty":"RSA","kid":"duplicate","n":"AQAB","e":"AQAB"}
                        ]}
                        """
        ), publicResolver()).load(ISSUER));
    }

    private static DefaultOidcMetadataSource source(
            QueueTransport transport,
            OidcHostAddressResolver resolver
    ) {
        OidcOutboundTargetPolicy policy = new OidcOutboundTargetPolicy(resolver, Set.of());
        return new DefaultOidcMetadataSource(
                new SecureOidcDocumentClient(policy, transport),
                policy
        );
    }

    private static OidcHostAddressResolver publicResolver() {
        return host -> List.of(InetAddress.getByName("93.184.216.34"));
    }

    private static String metadata(String issuer, String jwksUri, String algorithm) {
        return """
                {
                  "issuer": "%s",
                  "authorization_endpoint": "https://id.example.com/authorize",
                  "token_endpoint": "https://id.example.com/token",
                  "jwks_uri": "%s",
                  "response_types_supported": ["code"],
                  "id_token_signing_alg_values_supported": ["%s"]
                }
                """.formatted(issuer, jwksUri, algorithm);
    }

    static String jwks(String keyId) {
        return """
                {"keys":[{"kty":"RSA","kid":"%s","n":"AQAB","e":"AQAB"}]}
                """.formatted(keyId);
    }

    private static void assertUnavailable(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(OidcMetadataUnavailableException.class)
                .hasMessage("OIDC provider metadata is unavailable")
                .hasNoCause();
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private static final class QueueTransport implements OidcSingleHopHttpTransport {
        private final Queue<String> responses = new ArrayDeque<>();
        private final java.util.List<URI> requests = new java.util.ArrayList<>();

        private QueueTransport(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public OidcHttpResponse get(URI uri, Duration timeout) {
            requests.add(uri);
            String body = responses.remove();
            return new OidcHttpResponse(
                    200,
                    Map.of("Content-Type", List.of("application/json")),
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
            );
        }
    }
}
