package com.iflytek.skillhub.exception;

import com.iflytek.skillhub.domain.shared.exception.LocalizedMessage;
import org.springframework.http.HttpStatus;

/**
 * Common contract for errors that can be rendered as localized API responses.
 */
public interface LocalizedError extends LocalizedMessage {
    HttpStatus status();
}
