package com.iflytek.skillhub.domain.shared.exception;

/**
 * Domain exception used when a concurrent request prevents a safe state change.
 */
public class DomainConflictException extends LocalizedDomainException {

    public DomainConflictException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public int statusCode() {
        return 409;
    }
}
