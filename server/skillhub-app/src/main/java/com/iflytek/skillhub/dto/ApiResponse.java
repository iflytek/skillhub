package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

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
