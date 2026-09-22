package com.iflytek.skillhub.auth.connection.secret;

import java.util.Objects;

/** Authenticated ciphertext persisted at rest; byte accessors always return defensive copies. */
public final class EncryptedSecretEnvelope {

    private final String algorithm;
    private final String keyId;
    private final byte[] nonce;
    private final byte[] ciphertext;

    public EncryptedSecretEnvelope(
            String algorithm,
            String keyId,
            byte[] nonce,
            byte[] ciphertext
    ) {
        this.algorithm = requireText(algorithm, "algorithm");
        this.keyId = requireText(keyId, "keyId");
        this.nonce = Objects.requireNonNull(nonce, "nonce").clone();
        this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
        if (this.nonce.length != AesGcmSecretEnvelopeCipher.NONCE_BYTES) {
            throw new IllegalArgumentException("Secret envelope nonce length is invalid");
        }
        if (this.ciphertext.length <= AesGcmSecretEnvelopeCipher.TAG_BYTES) {
            throw new IllegalArgumentException("Secret envelope ciphertext length is invalid");
        }
    }

    public String algorithm() {
        return algorithm;
    }

    public String keyId() {
        return keyId;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "EncryptedSecretEnvelope[<redacted>]";
    }
}
