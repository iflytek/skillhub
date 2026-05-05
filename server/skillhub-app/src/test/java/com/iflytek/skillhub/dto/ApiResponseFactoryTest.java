package com.iflytek.skillhub.dto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiResponseFactoryTest {

    @AfterEach
    void clearContext() {
        MDC.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void ok_resolvesMessageAndIncludesRequestMetadata() {
        MessageSource messageSource = mock(MessageSource.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-05T12:00:00Z"), ZoneOffset.UTC);
        ApiResponseFactory factory = new ApiResponseFactory(messageSource, clock);
        LocaleContextHolder.setLocale(Locale.US);
        MDC.put("requestId", "req-1");
        when(messageSource.getMessage(eq("ok.code"), eq(new Object[]{"arg"}), eq("ok.code"), eq(Locale.US)))
                .thenReturn("resolved ok");

        ApiResponse<String> response = factory.ok("ok.code", "payload", "arg");

        assertThat(response.code()).isZero();
        assertThat(response.msg()).isEqualTo("resolved ok");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.timestamp()).isEqualTo(Instant.parse("2026-05-05T12:00:00Z"));
        assertThat(response.requestId()).isEqualTo("req-1");
    }

    @Test
    void error_resolvesMessageAndClearsData() {
        MessageSource messageSource = mock(MessageSource.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-05T12:30:00Z"), ZoneOffset.UTC);
        ApiResponseFactory factory = new ApiResponseFactory(messageSource, clock);
        LocaleContextHolder.setLocale(Locale.CHINA);
        when(messageSource.getMessage(eq("error.code"), eq(new Object[]{"arg"}), eq("error.code"), eq(Locale.CHINA)))
                .thenReturn("resolved error");

        ApiResponse<Void> response = factory.error(400, "error.code", "arg");

        assertThat(response.code()).isEqualTo(400);
        assertThat(response.msg()).isEqualTo("resolved error");
        assertThat(response.data()).isNull();
        assertThat(response.timestamp()).isEqualTo(Instant.parse("2026-05-05T12:30:00Z"));
    }

    @Test
    void errorMessage_usesLiteralMessage() {
        MessageSource messageSource = mock(MessageSource.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-05T13:00:00Z"), ZoneOffset.UTC);
        ApiResponseFactory factory = new ApiResponseFactory(messageSource, clock);

        ApiResponse<Void> response = factory.errorMessage(500, "literal");

        assertThat(response.code()).isEqualTo(500);
        assertThat(response.msg()).isEqualTo("literal");
        assertThat(response.data()).isNull();
        assertThat(response.timestamp()).isEqualTo(Instant.parse("2026-05-05T13:00:00Z"));
    }
}
