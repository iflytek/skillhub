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
    void doFilterInternal_truncatesLongRequestAndResponseBodiesInLogs(CapturedOutput output)
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

        assertThat(output).contains("Request Body: " + "x".repeat(512) + "... [truncated, original length=5000]");
        assertThat(output).contains("Response Body: " + "x".repeat(512) + "... [truncated, original length=5000]");
        assertThat(output).doesNotContain("Request Body: " + longBody);
        assertThat(output).doesNotContain("Response Body: " + longBody);
        assertThat(response.getContentAsString()).isEqualTo(longBody);
    }
}
