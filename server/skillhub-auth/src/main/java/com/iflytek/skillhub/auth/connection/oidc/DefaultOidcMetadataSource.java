package com.iflytek.skillhub.auth.connection.oidc;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Strict parser and secure remote loader for OIDC discovery metadata and public JWK sets. */
public final class DefaultOidcMetadataSource implements OidcMetadataSource {

    private static final Set<String> SAFE_SIGNING_ALGORITHMS = Set.of(
            "RS256", "RS384", "RS512", "PS256", "PS384", "PS512",
            "ES256", "ES384", "ES512", "EdDSA"
    );

    private final SecureOidcDocumentClient documents;
    private final OidcOutboundTargetPolicy targetPolicy;
    private final ObjectMapper objectMapper;

    public DefaultOidcMetadataSource(
            SecureOidcDocumentClient documents,
            OidcOutboundTargetPolicy targetPolicy
    ) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.targetPolicy = Objects.requireNonNull(targetPolicy, "targetPolicy");
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(20)
                        .maxStringLength(16 * 1024)
                        .maxNumberLength(128)
                        .build())
                .build();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.objectMapper = new ObjectMapper(factory);
    }

    @Override
    public OidcMetadataBundle load(OidcIssuer issuer) {
        OidcIssuer requiredIssuer = Objects.requireNonNull(issuer, "issuer");
        OidcProviderMetadata metadata = parseMetadata(
                requiredIssuer,
                documents.get(requiredIssuer.discoveryUri(), OidcDocumentKind.METADATA)
        );
        return new OidcMetadataBundle(metadata, refreshKeys(metadata));
    }

    @Override
    public OidcJwkSetSnapshot refreshKeys(OidcProviderMetadata metadata) {
        OidcProviderMetadata requiredMetadata = Objects.requireNonNull(metadata, "metadata");
        byte[] json = documents.get(requiredMetadata.jwksUri(), OidcDocumentKind.JWKS);
        try {
            JWKSet parsed = JWKSet.parse(new String(json, StandardCharsets.UTF_8));
            return new OidcJwkSetSnapshot(parsed.getKeys());
        } catch (ParseException | RuntimeException exception) {
            throw new OidcMetadataUnavailableException();
        }
    }

    private OidcProviderMetadata parseMetadata(OidcIssuer expectedIssuer, byte[] json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new OidcMetadataUnavailableException();
            }
            String returnedIssuer = requiredText(root, "issuer");
            if (!expectedIssuer.value().equals(returnedIssuer)) {
                throw new OidcMetadataUnavailableException();
            }
            URI authorizationEndpoint = endpoint(root, "authorization_endpoint");
            URI tokenEndpoint = endpoint(root, "token_endpoint");
            URI jwksUri = endpoint(root, "jwks_uri");
            targetPolicy.verify(authorizationEndpoint);
            targetPolicy.verify(tokenEndpoint);
            targetPolicy.verify(jwksUri);

            Set<String> responseTypes = stringSet(root, "response_types_supported", 32);
            if (!responseTypes.contains("code")) {
                throw new OidcMetadataUnavailableException();
            }
            Set<String> algorithms = stringSet(
                    root,
                    "id_token_signing_alg_values_supported",
                    32
            );
            algorithms.retainAll(SAFE_SIGNING_ALGORITHMS);
            if (algorithms.isEmpty()) {
                throw new OidcMetadataUnavailableException();
            }
            return new OidcProviderMetadata(
                    expectedIssuer,
                    authorizationEndpoint,
                    tokenEndpoint,
                    jwksUri,
                    algorithms
            );
        } catch (OidcMetadataUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OidcMetadataUnavailableException();
        }
    }

    private static URI endpoint(JsonNode root, String field) {
        URI uri;
        try {
            uri = URI.create(requiredText(root, field));
        } catch (IllegalArgumentException exception) {
            throw new OidcMetadataUnavailableException();
        }
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            throw new OidcMetadataUnavailableException();
        }
        return uri;
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new OidcMetadataUnavailableException();
        }
        return value.textValue();
    }

    private static Set<String> stringSet(JsonNode root, String field, int maximum) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray() || value.isEmpty() || value.size() > maximum) {
            throw new OidcMetadataUnavailableException();
        }
        Set<String> result = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 128) {
                throw new OidcMetadataUnavailableException();
            }
            result.add(item.textValue());
        }
        return result;
    }
}
