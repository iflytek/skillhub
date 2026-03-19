package com.iflytek.skillhub.domain.shared.exception;

/**
 * Domain exception used when the caller lacks permission for the requested business action.
 */
public class DomainForbiddenException extends LocalizedDomainException {

    public DomainForbiddenException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }
}
