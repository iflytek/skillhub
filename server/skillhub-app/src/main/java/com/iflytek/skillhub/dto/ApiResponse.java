package com.iflytek.skillhub.dto;

import java.time.Instant;

public record ApiResponse<T>(
        int code,
        String msg,
        T data,
        Instant timestamp,
        String requestId
) {}
