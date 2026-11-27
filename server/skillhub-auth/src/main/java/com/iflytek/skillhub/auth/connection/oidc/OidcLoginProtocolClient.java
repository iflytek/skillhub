package com.iflytek.skillhub.auth.connection.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Bounded OIDC protocol operations used by the organization login pilot.
 * Network access is delegated to the same DNS and response-limited client used by connection probes.
 */
@Service
public class OidcLoginProtocolClient {
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final OidcHttpDocumentClient http;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OidcLoginProtocolClient(OidcHttpDocumentClient http, ObjectMapper mapper, Clock clock) {
        this.http = Objects.requireNonNull(http);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public ProviderMetadata metadata(OidcConnectionConfiguration configuration) {
        try {
            JsonNode document = mapper.readTree(http.get(configuration.discoveryEndpoint()));
            if (document == null || !configuration.issuer().equals(document.path("issuer").asText(null))) {
                throw failure();
            }
            URI authorization = OidcConnectionConfiguration.requireHttpsEndpoint(
                    document.path("authorization_endpoint").asText(null));
            URI token = OidcConnectionConfiguration.requireHttpsEndpoint(
                    document.path("token_endpoint").asText(null));
            URI jwks = OidcConnectionConfiguration.requireHttpsEndpoint(
                    document.path("jwks_uri").asText(null));
            URI userInfo = null;
            if (document.path("userinfo_endpoint").isTextual()
                    && !document.path("userinfo_endpoint").asText().isBlank()) {
                userInfo = OidcConnectionConfiguration.requireHttpsEndpoint(document.path("userinfo_endpoint").asText());
            }
            return new ProviderMetadata(authorization, token, jwks, userInfo);
        } catch (RuntimeException | java.io.IOException failure) {
            throw failure();
        }
    }

    public TokenResponse exchangeCode(ProviderMetadata metadata, String clientId, String clientSecret,
                                      String code, String redirectUri, String codeVerifier) {
        try {
            JsonNode response = mapper.readTree(http.postForm(metadata.tokenEndpoint(), Map.of(
                    "grant_type", "authorization_code",
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "code", code,
                    "redirect_uri", redirectUri,
                    "code_verifier", codeVerifier
            )));
            String accessToken = text(response, "access_token");
            String idToken = text(response, "id_token");
            if (accessToken == null || idToken == null) {
                throw failure();
            }
            return new TokenResponse(accessToken, idToken);
        } catch (RuntimeException | java.io.IOException failure) {
            throw failure();
        }
    }

    public JsonNode userInfo(ProviderMetadata metadata, String accessToken) {
        if (metadata.userInfoEndpoint() == null) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode response = mapper.readTree(http.get(metadata.userInfoEndpoint(),
                    Map.of("Authorization", "Bearer " + accessToken)));
            if (response == null || !response.isObject()) {
                throw failure();
            }
            return response;
        } catch (RuntimeException | java.io.IOException failure) {
            throw failure();
        }
    }

    public JWTClaimsSet verifyIdToken(String compactToken, ProviderMetadata metadata,
                                      String issuer, String clientId, String nonce) {
        try {
            SignedJWT jwt = SignedJWT.parse(compactToken);
            String algorithm = jwt.getHeader().getAlgorithm().getName();
            if (algorithm == null || "none".equalsIgnoreCase(algorithm)) {
                throw failure();
            }
            JWKSet keys = JWKSet.parse(new String(http.get(metadata.jwksUri()), java.nio.charset.StandardCharsets.UTF_8));
            JWK key = selectKey(keys, jwt.getHeader().getKeyID());
            JWSVerifier verifier = new DefaultJWSVerifierFactory().createJWSVerifier(
                    jwt.getHeader(), publicKey(key));
            if (!jwt.verify(verifier)) {
                throw failure();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            boolean authorizedPartyValid = claims.getAudience().size() <= 1
                    || clientId.equals(claims.getStringClaim("azp"));
            if (!issuer.equals(claims.getIssuer())
                    || !claims.getAudience().contains(clientId)
                    || !authorizedPartyValid
                    || claims.getSubject() == null
                    || claims.getExpirationTime() == null
                    || claims.getExpirationTime().toInstant().isBefore(now.minus(CLOCK_SKEW))
                    || claims.getNotBeforeTime() != null
                    && claims.getNotBeforeTime().toInstant().isAfter(now.plus(CLOCK_SKEW))
                    || !nonce.equals(claims.getStringClaim("nonce"))) {
                throw failure();
            }
            return claims;
        } catch (RuntimeException | java.text.ParseException | com.nimbusds.jose.JOSEException failure) {
            throw failure();
        }
    }

    private static JWK selectKey(JWKSet keys, String keyId) {
        List<JWK> candidates = keys.getKeys().stream()
                .filter(key -> !key.isPrivate())
                .filter(key -> key.getKeyUse() == null || com.nimbusds.jose.jwk.KeyUse.SIGNATURE.equals(key.getKeyUse()))
                .filter(key -> keyId == null || keyId.equals(key.getKeyID()))
                .toList();
        if (candidates.size() != 1) {
            throw failure();
        }
        return candidates.getFirst();
    }

    private static java.security.Key publicKey(JWK key) throws com.nimbusds.jose.JOSEException {
        return switch (key.getKeyType().getValue()) {
            case "RSA" -> key.toRSAKey().toPublicKey();
            case "EC" -> key.toECKey().toPublicKey();
            case "OKP" -> key.toOctetKeyPair().toPublicKey();
            default -> throw failure();
        };
    }

    private static String text(JsonNode object, String field) {
        String value = object == null ? null : object.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("oidc protocol validation failed");
    }

    public record ProviderMetadata(URI authorizationEndpoint, URI tokenEndpoint, URI jwksUri,
                                   URI userInfoEndpoint) {}

    public record TokenResponse(String accessToken, String idToken) {}
}
