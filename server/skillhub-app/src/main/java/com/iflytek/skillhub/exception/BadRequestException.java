package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends LocalizedException {

    public BadRequestException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
