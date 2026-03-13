package com.iflytek.skillhub.auth.exception;

import org.springframework.http.HttpStatus;

public class AuthFlowException extends RuntimeException {

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

    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
