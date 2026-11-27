package com.iflytek.skillhub.auth.connection.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;

class OidcLoginProtocolClientTest {
    private static final String ISSUER = "https://idp.example.test";
    private static final String CLIENT_ID = "client-1";
    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

    @Test
    void verifiesSignatureIssuerAudienceExpirationAndNonce() throws Exception {
        OidcHttpDocumentClient http = mock(OidcHttpDocumentClient.class);
        var key = new RSAKeyGenerator(2048).keyID("kid-1").generate();
        when(http.get(URI.create("https://idp.example.test/jwks")))
                .thenReturn(new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8));
        OidcLoginProtocolClient client = new OidcLoginProtocolClient(http, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kid-1").type(JOSEObjectType.JWT).build(),
                new JWTClaimsSet.Builder().issuer(ISSUER).subject("subject-1").audience(CLIENT_ID)
                        .expirationTime(Date.from(NOW.plusSeconds(300))).claim("nonce", "nonce-1").build());
        jwt.sign(new RSASSASigner(key));

        JWTClaimsSet claims = client.verifyIdToken(jwt.serialize(),
                new OidcLoginProtocolClient.ProviderMetadata(null, null, URI.create("https://idp.example.test/jwks"), null),
                ISSUER, CLIENT_ID, "nonce-1");

        assertThat(claims.getSubject()).isEqualTo("subject-1");
    }

    private static String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        int index = signatureStart + (token.length() - signatureStart) / 2;
        char replacement = token.charAt(index) == 'a' ? 'b' : 'a';
        return token.substring(0, index) + replacement + token.substring(index + 1);
    }

    @Test
    void rejectsWrongNonceAndTamperedToken() throws Exception {
        OidcHttpDocumentClient http = mock(OidcHttpDocumentClient.class);
        var key = new RSAKeyGenerator(2048).keyID("kid-1").generate();
        when(http.get(URI.create("https://idp.example.test/jwks")))
                .thenReturn(new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8));
        OidcLoginProtocolClient client = new OidcLoginProtocolClient(http, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kid-1").build(),
                new JWTClaimsSet.Builder().issuer(ISSUER).subject("subject-1").audience(CLIENT_ID)
                        .expirationTime(Date.from(NOW.plusSeconds(300))).claim("nonce", "nonce-1").build());
        jwt.sign(new RSASSASigner(key));
        String token = jwt.serialize();
        // Mutate a character in the middle of the signature segment. Replacing the trailing
        // characters is not deterministic: when the original signature already ends with the
        // replacement, the token is unchanged and this test would pass a valid token through.
        String tampered = tamperSignature(token);
        assertThat(tampered).isNotEqualTo(token);
        var metadata = new OidcLoginProtocolClient.ProviderMetadata(null, null,
                URI.create("https://idp.example.test/jwks"), null);

        assertThatThrownBy(() -> client.verifyIdToken(token, metadata, ISSUER, CLIENT_ID, "wrong"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.verifyIdToken(tampered, metadata, ISSUER, CLIENT_ID, "nonce-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
