package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Source and evaluated priority persisted alongside one profile field value. */
public record ProfileFieldAuthority(ProfileValueSource source, int priority) {
    public ProfileFieldAuthority {
        Objects.requireNonNull(source, "source");
        if (priority <= 0) {
            throw new IllegalArgumentException("profile authority priority must be positive");
        }
    }
}
