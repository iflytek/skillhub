package com.iflytek.skillhub.auth.federation.core;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Email address whose ownership has already been established by a trusted adapter. */
public record VerifiedEmail(String value) {

    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$"
    );

    public VerifiedEmail {
        Objects.requireNonNull(value, "verified email must not be null");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !VALUE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("verified email is invalid");
        }
    }
}
