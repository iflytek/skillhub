package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

public class ScimProtocolException extends RuntimeException {
    private final HttpStatus status;
    private final String scimType;

    public ScimProtocolException(HttpStatus status, String detail) {
        this(status, null, detail);
    }

    public ScimProtocolException(HttpStatus status, String scimType, String detail) {
        super(detail);
        this.status = status;
        this.scimType = scimType;
    }

    public HttpStatus getStatus() { return status; }
    public String getScimType() { return scimType; }
}
