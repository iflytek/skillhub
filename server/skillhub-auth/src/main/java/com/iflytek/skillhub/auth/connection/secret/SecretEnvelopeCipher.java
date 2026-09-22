package com.iflytek.skillhub.auth.connection.secret;

/** Authenticated encryption seam used by Secret persistence. */
public interface SecretEnvelopeCipher {

    EncryptedSecretEnvelope encrypt(SecretBindingContext context, SecretMaterial plaintext);

    SecretMaterial decrypt(SecretBindingContext context, EncryptedSecretEnvelope envelope);
}
