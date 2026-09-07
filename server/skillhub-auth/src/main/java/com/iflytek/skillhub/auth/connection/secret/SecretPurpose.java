package com.iflytek.skillhub.auth.connection.secret;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, extensible purpose key used to prevent Secret reuse across protocols. */
public record SecretPurpose(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9._-]{2,127}");

    public static final SecretPurpose LOGIN_CLIENT_SECRET =
            new SecretPurpose("login.client-secret");

    public SecretPurpose {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Secret purpose format is invalid");
        }
    }
}
