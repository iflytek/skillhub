package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-layer exception mapped to HTTP 400 with a localized error code.
 */
public class BadRequestException extends LocalizedException {

    public BadRequestException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
