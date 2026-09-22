package com.iflytek.skillhub.auth.federation.config;

import com.iflytek.skillhub.auth.connection.secret.InMemorySecretEnvelopeKeyring;
import com.iflytek.skillhub.auth.connection.secret.SecretEnvelopeKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Operator-owned keyring configuration; key material is never rendered by this type. */
@ConfigurationProperties(prefix = "skillhub.enterprise.secret-envelope")
public class EnterpriseSecretEnvelopeProperties {

    private String activeKeyId = "";
    private List<String> keys = List.of();

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId == null ? "" : activeKeyId.trim();
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys == null ? List.of() : List.copyOf(keys);
    }

    InMemorySecretEnvelopeKeyring createKeyring() {
        if (activeKeyId.isBlank()) {
            throw new IllegalStateException("Enterprise Secret envelope active key id is required");
        }
        Map<String, byte[]> decoded = new LinkedHashMap<>();
        byte[] activeBytes = null;
        try {
            for (String entry : keys) {
                int separator = entry == null ? -1 : entry.indexOf(':');
                if (separator < 3 || separator == entry.length() - 1) {
                    throw invalidKeyring();
                }
                String keyId = entry.substring(0, separator).trim();
                byte[] key = Base64.getDecoder().decode(entry.substring(separator + 1).trim());
                if (decoded.putIfAbsent(keyId, key) != null) {
                    Arrays.fill(key, (byte) 0);
                    throw invalidKeyring();
                }
            }
            activeBytes = decoded.remove(activeKeyId);
            if (activeBytes == null) {
                throw invalidKeyring();
            }
            SecretEnvelopeKey active = null;
            List<SecretEnvelopeKey> previous = new ArrayList<>();
            try {
                active = new SecretEnvelopeKey(activeKeyId, activeBytes);
                decoded.forEach((keyId, bytes) ->
                        previous.add(new SecretEnvelopeKey(keyId, bytes)));
                return new InMemorySecretEnvelopeKeyring(
                        active,
                        previous.toArray(SecretEnvelopeKey[]::new)
                );
            } finally {
                if (active != null) {
                    active.close();
                }
                previous.forEach(SecretEnvelopeKey::close);
            }
        } catch (IllegalArgumentException invalid) {
            throw invalidKeyring();
        } finally {
            if (activeBytes != null) {
                Arrays.fill(activeBytes, (byte) 0);
            }
            decoded.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        }
    }

    private static IllegalStateException invalidKeyring() {
        return new IllegalStateException("Enterprise Secret envelope keyring is invalid");
    }

    @Override
    public String toString() {
        return "EnterpriseSecretEnvelopeProperties[activeKeyId=<redacted>, keys=<redacted>]";
    }
}
