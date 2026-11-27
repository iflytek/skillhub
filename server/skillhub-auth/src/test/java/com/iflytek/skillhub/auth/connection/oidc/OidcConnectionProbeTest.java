package com.iflytek.skillhub.auth.connection.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OidcConnectionProbeTest {

    private static final String ISSUER = "https://idp.example.org/realms/platform";
    private static final String JWKS = "https://keys.example.org/signing-keys";
    private static final OidcConnectionConfiguration CONFIG = new OidcConnectionConfiguration(ISSUER, "skillhub");

    @Test
    void fetchesOnlyDiscoveryAndPublicSigningKeys() throws Exception {
        var signingKey = new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).generate().toPublicJWK();
        var requested = new ArrayList<URI>();
        var probe = new OidcConnectionProbe(uri -> {
            requested.add(uri);
            return uri.equals(URI.create(JWKS))
                    ? bytes(new JWKSet(signingKey).toString())
                    : metadata(ISSUER, JWKS);
        }, new ObjectMapper());

        probe.verify(CONFIG);

        assertThat(requested).containsExactly(
                URI.create(ISSUER + "/.well-known/openid-configuration"), URI.create(JWKS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{} {}"})
    void rejectsInvalidMetadataWithAStableFailureReason(String document) {
        var probe = new OidcConnectionProbe(uri -> bytes(document), new ObjectMapper());

        assertThatThrownBy(() -> probe.verify(CONFIG))
                .isInstanceOfSatisfying(OidcConnectionProbe.Failure.class,
                        failure -> assertThat(failure.getReason()).isEqualTo(OidcConnectionProbe.Reason.INVALID_METADATA));
    }

    @Test
    void rejectsIssuerMismatchAndInsecureJwksBeforeResolvingThem() {
        for (byte[] malformed : List.of(metadata(ISSUER + "/", JWKS), metadata(ISSUER, "http://keys.example.org"))) {
            var requested = new ArrayList<URI>();
            var probe = new OidcConnectionProbe(uri -> {
                requested.add(uri);
                return malformed;
            }, new ObjectMapper());

            assertThatThrownBy(() -> probe.verify(CONFIG))
                    .isInstanceOfSatisfying(OidcConnectionProbe.Failure.class,
                            failure -> assertThat(failure.getReason()).isEqualTo(OidcConnectionProbe.Reason.INVALID_METADATA));
            assertThat(requested).containsExactly(URI.create(ISSUER + "/.well-known/openid-configuration"));
        }
    }

    @Test
    void rejectsAKeySetWithoutAnyPublicSigningKey() throws Exception {
        var encryptionKey = new RSAKeyGenerator(2048).keyUse(KeyUse.ENCRYPTION).generate().toPublicJWK();
        var probe = new OidcConnectionProbe(uri -> uri.equals(URI.create(JWKS))
                ? bytes(new JWKSet(encryptionKey).toString())
                : metadata(ISSUER, JWKS), new ObjectMapper());

        assertThatThrownBy(() -> probe.verify(CONFIG))
                .isInstanceOfSatisfying(OidcConnectionProbe.Failure.class,
                        failure -> assertThat(failure.getReason()).isEqualTo(OidcConnectionProbe.Reason.INVALID_METADATA));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://idp.example.org",
            "https://idp.example.org?query",
            "https://idp.example.org:65536",
            "https://user@idp.example.org",
            "https://192.0.2.1",
            "https://[2001:db8::1]",
            "https://idp.example.org/realm/../other"
    })
    void rejectsInvalidIssuerConfiguration(String issuer) {
        assertThatThrownBy(() -> new OidcConnectionConfiguration(issuer, CONFIG.clientId()))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.loginConnection.config.invalid");
    }

    private static byte[] metadata(String issuer, String jwksUri) {
        return bytes("""
                {"issuer":"%s","authorization_endpoint":"https://idp.example.org/authorize",
                 "token_endpoint":"https://idp.example.org/token","jwks_uri":"%s"}
                """.formatted(issuer, jwksUri));
    }

    private static byte[] bytes(String document) {
        return document.getBytes(StandardCharsets.UTF_8);
    }
}
