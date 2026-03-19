package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

/**
 * Common contract for errors that can be rendered as localized API responses.
 */
public interface LocalizedError {
    String messageCode();

    Object[] messageArgs();

    HttpStatus status();
}
