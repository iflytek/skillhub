package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;
import java.util.Set;

/** Typed normalized attribute plus the assurance evidence attached to that fact. */
public record NormalizedAttribute(AttributeKey key, AttributeValue value, Set<Assurance> assurance) {

    public NormalizedAttribute {
        Objects.requireNonNull(key, "attribute key must not be null");
        Objects.requireNonNull(value, "attribute value must not be null");
        Objects.requireNonNull(assurance, "attribute assurance must not be null");
        assurance = Set.copyOf(assurance);
    }
}
