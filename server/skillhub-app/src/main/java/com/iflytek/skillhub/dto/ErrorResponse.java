package com.iflytek.skillhub.dto;

public record ErrorResponse(
        int status,
        String error,
        String message
) {}
