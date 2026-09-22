package com.iflytek.skillhub.auth.connection.secret;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM local Secret envelope implementation with context-bound authenticated data. */
public final class AesGcmSecretEnvelopeCipher implements SecretEnvelopeCipher {

    public static final String ALGORITHM = "AES-256-GCM";
    static final int NONCE_BYTES = 12;
    static final int TAG_BYTES = 16;
    private static final int TAG_BITS = TAG_BYTES * Byte.SIZE;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretEnvelopeKeyring keyring;
    private final SecureRandom secureRandom;

    public AesGcmSecretEnvelopeCipher(SecretEnvelopeKeyring keyring) {
        this(keyring, new SecureRandom());
    }

    AesGcmSecretEnvelopeCipher(SecretEnvelopeKeyring keyring, SecureRandom secureRandom) {
        this.keyring = Objects.requireNonNull(keyring, "keyring");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public EncryptedSecretEnvelope encrypt(
            SecretBindingContext context,
            SecretMaterial plaintext
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(plaintext, "plaintext");
        try (SecretEnvelopeKey key = keyring.activeKey()) {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            byte[] ciphertext = plaintext.use(bytes -> encrypt(
                    bytes,
                    key,
                    nonce,
                    context.authenticatedData(ALGORITHM, key.keyId())
            ));
            return new EncryptedSecretEnvelope(ALGORITHM, key.keyId(), nonce, ciphertext);
        }
    }

    @Override
    public SecretMaterial decrypt(
            SecretBindingContext context,
            EncryptedSecretEnvelope envelope
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(envelope, "envelope");
        if (!ALGORITHM.equals(envelope.algorithm())) {
            throw new SecretMaterialUnavailableException();
        }
        SecretEnvelopeKey key = keyring.findById(envelope.keyId())
                .orElseThrow(SecretMaterialUnavailableException::new);
        try (key) {
            byte[] plaintext = decrypt(
                    envelope.ciphertext(),
                    key,
                    envelope.nonce(),
                    context.authenticatedData(envelope.algorithm(), envelope.keyId())
            );
            try {
                return SecretMaterial.copyOf(plaintext);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (SecretMaterialUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretMaterialUnavailableException();
        }
    }

    private static byte[] encrypt(
            byte[] plaintext,
            SecretEnvelopeKey key,
            byte[] nonce,
            byte[] authenticatedData
    ) {
        try {
            return crypt(Cipher.ENCRYPT_MODE, plaintext, key, nonce, authenticatedData);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt Secret material", exception);
        }
    }

    private static byte[] decrypt(
            byte[] ciphertext,
            SecretEnvelopeKey key,
            byte[] nonce,
            byte[] authenticatedData
    ) {
        try {
            return crypt(Cipher.DECRYPT_MODE, ciphertext, key, nonce, authenticatedData);
        } catch (GeneralSecurityException exception) {
            throw new SecretMaterialUnavailableException();
        }
    }

    private static byte[] crypt(
            int mode,
            byte[] input,
            SecretEnvelopeKey key,
            byte[] nonce,
            byte[] authenticatedData
    ) throws GeneralSecurityException {
        byte[] keyBytes = key.copyKeyBytes();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    mode,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce)
            );
            cipher.updateAAD(authenticatedData);
            return cipher.doFinal(input);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
}
