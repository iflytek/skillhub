package com.iflytek.skillhub.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveLogSanitizerTest {

    private final SensitiveLogSanitizer sanitizer = new SensitiveLogSanitizer();

    @Test
    void shouldRedactSensitiveQueryParameters() {
        String sanitized = sanitizer.sanitizeQuery("returnTo=%2Fdashboard&token=abc123&password=secret&code=xyz");

        assertThat(sanitized).contains("returnTo=%2Fdashboard");
        assertThat(sanitized).contains("token=[REDACTED]");
        assertThat(sanitized).contains("password=[REDACTED]");
        assertThat(sanitized).contains("code=[REDACTED]");
    }
}
