package com.iflytek.skillhub.domain.shared.exception;

/**
 * Base class for domain-layer exceptions that carry a localized message code and arguments.
 */
public abstract class LocalizedDomainException extends RuntimeException {

    private final String messageCode;
    private final Object[] messageArgs;

    protected LocalizedDomainException(String messageCode, Object... messageArgs) {
        super(messageCode);
        this.messageCode = messageCode;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }

    public String messageCode() {
        return messageCode;
    }

    public Object[] messageArgs() {
        return messageArgs.clone();
    }
}
