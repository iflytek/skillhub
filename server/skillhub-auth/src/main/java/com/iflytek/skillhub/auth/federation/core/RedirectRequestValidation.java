package com.iflytek.skillhub.auth.federation.core;

import java.net.URI;
import java.util.Objects;

final class RedirectRequestValidation {

    private static final int MAX_TRANSACTION_ID_LENGTH = 512;

    private RedirectRequestValidation() {
    }

    static String requireTransactionId(String value) {
        Objects.requireNonNull(value, "browser transaction id must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("browser transaction id must not be blank");
        }
        if (value.length() > MAX_TRANSACTION_ID_LENGTH || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("browser transaction id is invalid");
        }
        return value;
    }

    static URI requireAbsoluteUri(URI value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (!value.isAbsolute()) {
            throw new IllegalArgumentException(field + " must be absolute");
        }
        return value;
    }
}
