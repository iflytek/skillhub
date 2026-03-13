package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends LocalizedException {

    public ForbiddenException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
