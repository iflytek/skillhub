package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-layer exception mapped to HTTP 401 with a localized error code.
 */
public class UnauthorizedException extends LocalizedException {

    public UnauthorizedException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }
}
