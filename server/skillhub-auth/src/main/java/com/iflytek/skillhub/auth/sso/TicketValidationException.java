package com.iflytek.skillhub.auth.sso;

/**
 * Thrown when the SSO ticket-validation endpoint rejects a ticket or returns
 * an unexpected response.
 */
public class TicketValidationException extends RuntimeException {

    public TicketValidationException(String message) {
        super(message);
    }

    public TicketValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
