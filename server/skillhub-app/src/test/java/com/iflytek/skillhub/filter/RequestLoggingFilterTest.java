package com.iflytek.skillhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    @Test
    void doFilterInternal_truncatesLongRequestBodyAndOmitsResponseBody(CapturedOutput output)
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        String longBody = "x".repeat(5_000);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContentType("application/json");
        request.setContent(longBody.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        FilterChain filterChain = (req, res) -> {
            req.getReader().lines().count();
            res.setContentType("application/json");
            res.getWriter().write(longBody);
        };

        filter.doFilter(request, response, filterChain);

        // Request body should be truncated at 200 chars
        assertThat(output).contains("Body: " + "x".repeat(200) + "...[truncated]");
        assertThat(output).doesNotContain("Body: " + longBody);
        // Response body should not be logged at all
        assertThat(output).doesNotContain("Response Body:");
        // Original response should still be intact
        assertThat(response.getContentAsString()).isEqualTo(longBody);
    }

    @Test
    void doFilterInternal_skipsActuatorEndpoints(CapturedOutput output)
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {};

        filter.doFilter(request, response, filterChain);

        assertThat(output).doesNotContain("/actuator/health");
    }

    @Test
    void doFilterInternal_logsCoreSummaryFields(CapturedOutput output)
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {};

        filter.doFilter(request, response, filterChain);

        assertThat(output).contains("GET /api/v1/skills");
        assertThat(output).contains("200");
        assertThat(output).contains("127.0.0.1");
        assertThat(output).contains("ms");
        // Should not contain full headers dump
        assertThat(output).doesNotContain("Headers: {");
    }
}
