package com.iflytek.skillhub.auth.exception;

import com.iflytek.skillhub.domain.shared.exception.LocalizedMessage;
import org.springframework.http.HttpStatus;

/**
 * Auth-layer exception that carries both an HTTP status and a localized message code for API
 * rendering.
 */
public class AuthFlowException extends RuntimeException implements LocalizedMessage {

    private final HttpStatus status;
    private final String messageCode;
    private final Object[] messageArgs;

    public AuthFlowException(HttpStatus status, String messageCode, Object... messageArgs) {
        super(messageCode);
        this.status = status;
        this.messageCode = messageCode;
        this.messageArgs = messageArgs;
    }

    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String messageCode() {
        return messageCode;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
