package com.iflytek.skillhub.dto;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Clock;
import java.time.Instant;

@Component
public class ApiResponseFactory {

    private final MessageSource messageSource;
    private final Clock clock;

    public ApiResponseFactory(MessageSource messageSource, Clock clock) {
        this.messageSource = messageSource;
        this.clock = clock;
    }

    public <T> ApiResponse<T> ok(String messageCode, T data, Object... args) {
        String msg = messageSource.getMessage(messageCode, args, messageCode, LocaleContextHolder.getLocale());
        return new ApiResponse<>(0, msg, data, Instant.now(clock), MDC.get("requestId"));
    }

    public ApiResponse<Void> error(int code, String messageCode, Object... args) {
        String msg = messageSource.getMessage(messageCode, args, messageCode, LocaleContextHolder.getLocale());
        return new ApiResponse<>(code, msg, null, Instant.now(clock), MDC.get("requestId"));
    }

    public ApiResponse<Void> errorMessage(int code, String msg) {
        return new ApiResponse<>(code, msg, null, Instant.now(clock), MDC.get("requestId"));
    }
}
