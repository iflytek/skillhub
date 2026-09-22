package com.iflytek.skillhub.auth.connection.secret;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AesGcmSecretEnvelopeCipherTest {

    private static final SecretBindingContext CONTEXT = new SecretBindingContext(
            "organization-1",
            "connection-1",
            SecretPurpose.LOGIN_CLIENT_SECRET,
            1
    );

    private final InMemorySecretEnvelopeKeyring keyring = keyring("key-2026-09", (byte) 7);
    private final AesGcmSecretEnvelopeCipher cipher =
            new AesGcmSecretEnvelopeCipher(keyring, new SecureRandom());

    @AfterEach
    void closeKeyring() {
        keyring.close();
    }

    @Test
    void encryptsWithRandomNonceAndDecryptsOnlyInTheBoundContext() {
        EncryptedSecretEnvelope first;
        EncryptedSecretEnvelope second;
        try (SecretMaterial secret = SecretMaterial.copyOf("client-secret-value".getBytes(UTF_8))) {
            first = cipher.encrypt(CONTEXT, secret);
            second = cipher.encrypt(CONTEXT, secret);
        }

        assertThat(first.nonce()).hasSize(12).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        try (SecretMaterial decrypted = cipher.decrypt(CONTEXT, first)) {
            assertThat(read(decrypted)).isEqualTo("client-secret-value");
        }

        SecretBindingContext anotherConnection = new SecretBindingContext(
                "organization-1",
                "connection-2",
                SecretPurpose.LOGIN_CLIENT_SECRET,
                1
        );
        assertUnavailable(() -> cipher.decrypt(anotherConnection, first));
    }

    @Test
    void failsClosedForTamperingOrUnknownMasterKeyWithoutLeakingDetails() {
        EncryptedSecretEnvelope encrypted;
        try (SecretMaterial secret = SecretMaterial.copyOf("do-not-log-me".getBytes(UTF_8))) {
            encrypted = cipher.encrypt(CONTEXT, secret);
        }
        byte[] tampered = encrypted.ciphertext();
        tampered[tampered.length - 1] ^= 1;
        EncryptedSecretEnvelope changed = new EncryptedSecretEnvelope(
                encrypted.algorithm(),
                encrypted.keyId(),
                encrypted.nonce(),
                tampered
        );

        assertUnavailable(() -> cipher.decrypt(CONTEXT, changed));

        try (InMemorySecretEnvelopeKeyring anotherKeyring = keyring("another-key", (byte) 9)) {
            AesGcmSecretEnvelopeCipher anotherCipher =
                    new AesGcmSecretEnvelopeCipher(anotherKeyring, new SecureRandom());
            assertUnavailable(() -> anotherCipher.decrypt(CONTEXT, encrypted));
        }

        assertThat(encrypted.toString())
                .doesNotContain("do-not-log-me", encrypted.keyId())
                .contains("<redacted>");
    }

    @Test
    void secretAndKeyBuffersAreDefensivelyCopiedAndWipedOnClose() {
        byte[] source = "short-lived-secret".getBytes(UTF_8);
        SecretMaterial material = SecretMaterial.copyOf(source);
        Arrays.fill(source, (byte) 0);

        assertThat(read(material)).isEqualTo("short-lived-secret");
        assertThat(material.toString()).isEqualTo("SecretMaterial[<redacted>]");
        material.close();

        assertThatThrownBy(() -> read(material))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Secret material is closed");

        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 3);
        SecretEnvelopeKey key = new SecretEnvelopeKey("key-copy-test", keyBytes);
        Arrays.fill(keyBytes, (byte) 0);
        assertThat(key.copyKeyBytes()).containsOnly((byte) 3);
        assertThat(key.toString()).isEqualTo("SecretEnvelopeKey[<redacted>]");
        key.close();
    }

    @Test
    void keyringRotationCanDecryptOldEnvelopeWithoutReusingTheRetiredKeyForWrites() {
        EncryptedSecretEnvelope oldEnvelope;
        try (SecretMaterial material = SecretMaterial.copyOf("old-envelope".getBytes(UTF_8))) {
            oldEnvelope = cipher.encrypt(CONTEXT, material);
        }

        SecretEnvelopeKey newKey = key("key-2026-10", (byte) 10);
        SecretEnvelopeKey oldKey = key("key-2026-09", (byte) 7);
        try (newKey;
             oldKey;
             InMemorySecretEnvelopeKeyring rotatedKeyring =
                     new InMemorySecretEnvelopeKeyring(newKey, oldKey)) {
            AesGcmSecretEnvelopeCipher rotatedCipher =
                    new AesGcmSecretEnvelopeCipher(rotatedKeyring, new SecureRandom());
            try (SecretMaterial decrypted = rotatedCipher.decrypt(CONTEXT, oldEnvelope)) {
                assertThat(read(decrypted)).isEqualTo("old-envelope");
            }
            try (SecretMaterial next = SecretMaterial.copyOf("new-envelope".getBytes(UTF_8))) {
                assertThat(rotatedCipher.encrypt(CONTEXT, next).keyId())
                        .isEqualTo("key-2026-10");
            }
        }
    }

    private static String read(SecretMaterial material) {
        return material.use(bytes -> new String(bytes, UTF_8));
    }

    private static InMemorySecretEnvelopeKeyring keyring(String keyId, byte value) {
        SecretEnvelopeKey key = key(keyId, value);
        try (key) {
            return new InMemorySecretEnvelopeKeyring(key);
        }
    }

    private static SecretEnvelopeKey key(String keyId, byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        SecretEnvelopeKey key = new SecretEnvelopeKey(keyId, bytes);
        Arrays.fill(bytes, (byte) 0);
        return key;
    }

    private static void assertUnavailable(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(SecretMaterialUnavailableException.class)
                .hasMessage("Secret material is unavailable")
                .hasNoCause();
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
