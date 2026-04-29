package com.iflytek.skillhub.auth.uass;

/**
 * Raised when the UASS adapter cannot complete an upstream operation without
 * exposing raw callback payloads or jar-specific error shapes.
 */
public class UassClientException extends RuntimeException {

    private final String operation;

    public UassClientException(String operation, String message) {
        super(message);
        this.operation = operation;
    }

    public UassClientException(String operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}
