package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(
        int code,
        String msg,
        T data,
        Instant timestamp,
        String requestId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> errorArgs
) {
    public ApiResponse(int code, String msg, T data, Instant timestamp, String requestId) {
        this(code, msg, data, timestamp, requestId, null, null);
    }
}
