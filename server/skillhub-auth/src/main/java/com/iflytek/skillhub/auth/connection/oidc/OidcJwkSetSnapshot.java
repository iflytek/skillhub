package com.iflytek.skillhub.auth.connection.oidc;

import com.nimbusds.jose.jwk.JWK;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validated public JWK set with deterministic key-id lookup. */
public record OidcJwkSetSnapshot(List<JWK> keys) {

    public OidcJwkSetSnapshot {
        keys = List.copyOf(Objects.requireNonNull(keys, "keys"));
        if (keys.isEmpty() || keys.size() > 64) {
            throw new OidcMetadataUnavailableException();
        }
        if (keys.stream().anyMatch(JWK::isPrivate)) {
            throw new OidcMetadataUnavailableException();
        }
        Set<String> keyIds = new HashSet<>();
        if (keys.stream()
                .map(JWK::getKeyID)
                .filter(Objects::nonNull)
                .anyMatch(keyId -> keyId.isBlank()
                        || keyId.length() > 128
                        || !keyIds.add(keyId))) {
            throw new OidcMetadataUnavailableException();
        }
    }

    public JWK findByKeyId(String keyId) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 128) {
            throw new OidcMetadataUnavailableException();
        }
        List<JWK> matching = keys.stream()
                .filter(key -> keyId.equals(key.getKeyID()))
                .toList();
        if (matching.size() != 1) {
            throw new OidcMetadataUnavailableException();
        }
        return matching.get(0);
    }

    public boolean containsKeyId(String keyId) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 128) {
            return false;
        }
        return keys.stream().filter(key -> keyId.equals(key.getKeyID())).limit(2).count() == 1;
    }
}
