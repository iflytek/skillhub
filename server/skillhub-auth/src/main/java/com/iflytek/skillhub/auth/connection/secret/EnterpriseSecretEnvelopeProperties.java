package com.iflytek.skillhub.auth.connection.secret;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillhub.enterprise.secret-envelope")
public class EnterpriseSecretEnvelopeProperties {

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{2,127}");
    private static final int KEY_BYTES = 32;

    private String activeKeyId = "";
    private List<String> keys = List.of();

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId.strip();
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = List.copyOf(keys);
    }

    byte[] activeKey() {
        return key(activeKeyId);
    }

    byte[] key(String requestedKeyId) {
        Set<String> ids = new HashSet<>();
        byte[] requested = null;
        boolean success = false;
        try {
            for (String entry : keys) {
                String[] parts = entry.strip().split(":", 2);
                if (parts.length != 2 || !KEY_ID.matcher(parts[0]).matches() || !ids.add(parts[0])) {
                    throw invalidKeyring();
                }
                byte[] key = Base64.getDecoder().decode(parts[1]);
                if (key.length != KEY_BYTES || !parts[0].equals(requestedKeyId)) {
                    Arrays.fill(key, (byte) 0);
                    if (key.length != KEY_BYTES) {
                        throw invalidKeyring();
                    }
                } else {
                    requested = key;
                }
            }
            if (requested == null) {
                throw invalidKeyring();
            }
            success = true;
            return requested;
        } catch (IllegalArgumentException invalid) {
            throw invalidKeyring();
        } finally {
            if (!success && requested != null) {
                Arrays.fill(requested, (byte) 0);
            }
        }
    }

    private static IllegalStateException invalidKeyring() {
        return new IllegalStateException("OIDC secret envelope keyring is unavailable");
    }
}
