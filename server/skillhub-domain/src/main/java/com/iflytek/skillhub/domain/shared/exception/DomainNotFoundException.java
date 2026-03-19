package com.iflytek.skillhub.domain.shared.exception;

/**
 * Domain exception used when a required business entity cannot be found.
 */
public class DomainNotFoundException extends LocalizedDomainException {

    public DomainNotFoundException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }
}
