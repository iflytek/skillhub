package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-layer exception mapped to HTTP 403 with a localized error code.
 */
public class ForbiddenException extends LocalizedException {

    public ForbiddenException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
