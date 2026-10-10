package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Normalized evidence about how strongly an identity fact was established. */
public record Assurance(String value) {

    private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z][a-z0-9._:-]{0,127}");

    public Assurance {
        Objects.requireNonNull(value, "assurance must not be null");
        value = value.trim();
        if (!VALUE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("assurance must be a normalized stable key");
        }
    }
}
