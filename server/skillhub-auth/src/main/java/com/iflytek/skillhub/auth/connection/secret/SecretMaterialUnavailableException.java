package com.iflytek.skillhub.auth.connection.secret;

/** Privacy-preserving data-plane failure shared by all unavailable Secret causes. */
public final class SecretMaterialUnavailableException extends RuntimeException {

    public SecretMaterialUnavailableException() {
        super("Secret material is unavailable");
    }
}
