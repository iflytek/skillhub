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

    @Test
    void sanitizeRequestTarget_withNullQuery_returnsUriOnly() {
        jakarta.servlet.http.HttpServletRequest request = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/v1/skills");
        org.mockito.Mockito.when(request.getQueryString()).thenReturn(null);

        String result = sanitizer.sanitizeRequestTarget(request);

        assertThat(result).isEqualTo("/api/v1/skills");
    }

    @Test
    void sanitizeRequestTarget_withEmptyQuery_returnsUriOnly() {
        jakarta.servlet.http.HttpServletRequest request = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/v1/skills");
        org.mockito.Mockito.when(request.getQueryString()).thenReturn("");

        String result = sanitizer.sanitizeRequestTarget(request);

        assertThat(result).isEqualTo("/api/v1/skills");
    }

    @Test
    void sanitizeQuery_withPartMissingEquals_returnsPartUnchanged() {
        String sanitized = sanitizer.sanitizeQuery("returnTo=%2Fdashboard&noequals");

        assertThat(sanitized).contains("returnTo=%2Fdashboard");
        assertThat(sanitized).contains("noequals");
    }

    @Test
    void sanitizeRequestTarget_withQuery_returnsUriWithSanitizedQuery() {
        jakarta.servlet.http.HttpServletRequest request = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/v1/skills");
        org.mockito.Mockito.when(request.getQueryString()).thenReturn("token=secret&name=test");

        String result = sanitizer.sanitizeRequestTarget(request);

        assertThat(result).isEqualTo("/api/v1/skills?token=[REDACTED]&name=test");
    }
}
