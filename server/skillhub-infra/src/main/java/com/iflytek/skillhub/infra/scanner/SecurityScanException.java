package com.iflytek.skillhub.infra.scanner;

public class SecurityScanException extends RuntimeException {

    public SecurityScanException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecurityScanException(String message) {
        super(message);
    }
}
