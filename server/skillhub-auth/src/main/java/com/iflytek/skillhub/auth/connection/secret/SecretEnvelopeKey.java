package com.iflytek.skillhub.auth.connection.secret;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/** Erasable AES-256 envelope key identified by a non-secret rotation key id. */
public final class SecretEnvelopeKey implements AutoCloseable {

    private static final Pattern KEY_ID_FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{2,127}");
    private static final int AES_256_KEY_BYTES = 32;

    private final String keyId;
    private byte[] keyBytes;

    public SecretEnvelopeKey(String keyId, byte[] keyBytes) {
        Objects.requireNonNull(keyId, "keyId");
        String normalizedKeyId = keyId.trim();
        if (!KEY_ID_FORMAT.matcher(normalizedKeyId).matches()) {
            throw new IllegalArgumentException("Secret envelope key id format is invalid");
        }
        Objects.requireNonNull(keyBytes, "keyBytes");
        if (keyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalArgumentException("Secret envelope key must contain 32 bytes");
        }
        this.keyId = normalizedKeyId;
        this.keyBytes = keyBytes.clone();
    }

    public String keyId() {
        return keyId;
    }

    public synchronized byte[] copyKeyBytes() {
        if (keyBytes == null) {
            throw new IllegalStateException("Secret envelope key is closed");
        }
        return keyBytes.clone();
    }

    @Override
    public synchronized void close() {
        if (keyBytes != null) {
            Arrays.fill(keyBytes, (byte) 0);
            keyBytes = null;
        }
    }

    @Override
    public String toString() {
        return "SecretEnvelopeKey[<redacted>]";
    }
}
