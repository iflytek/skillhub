package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** A case-preserving immutable identifier in an adapter-defined subject namespace. */
public record SubjectRef(SubjectType type, String value) {

    private static final int MAX_VALUE_LENGTH = 1024;

    public SubjectRef {
        Objects.requireNonNull(type, "subject type must not be null");
        Objects.requireNonNull(value, "subject value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("subject value must not be blank");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("subject value exceeds " + MAX_VALUE_LENGTH + " characters");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("subject value must not contain control characters");
        }
    }
}
