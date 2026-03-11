package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String requestId,
        Instant timestamp
) {
    public ErrorResponse(int status, String error, String message, String requestId) {
        this(status, error, message, requestId, Instant.now());
    }

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null, Instant.now());
    }
}
