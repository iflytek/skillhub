package com.iflytek.skillhub.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void doFilterInternal_truncatesLongRequestBodyAndOmitsResponseBody()
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        String longBody = "x".repeat(5_000);
        attachAppender();

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

        List<String> loggedMessages = loggedMessages();
        assertThat(loggedMessages).anySatisfy(message ->
                assertThat(message).contains("Body: " + "x".repeat(200) + "...[truncated]"));
        assertThat(loggedMessages).noneMatch(message -> message.contains("Body: " + longBody));
        assertThat(loggedMessages).noneMatch(message -> message.contains("Response Body:"));
        assertThat(response.getContentAsString()).isEqualTo(longBody);
    }

    @Test
    void doFilterInternal_skipsActuatorEndpoints()
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        attachAppender();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {};

        filter.doFilter(request, response, filterChain);

        assertThat(loggedMessages()).noneMatch(message -> message.contains("/actuator/health"));
    }

    @Test
    void doFilterInternal_logsCoreSummaryFields()
            throws ServletException, IOException {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        attachAppender();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {};

        filter.doFilter(request, response, filterChain);

        assertThat(loggedMessages()).anySatisfy(message -> {
            assertThat(message).contains("GET /api/v1/skills");
            assertThat(message).contains("200");
            assertThat(message).contains("127.0.0.1");
            assertThat(message).contains("ms");
        });
        assertThat(loggedMessages()).noneMatch(message -> message.contains("Headers: {"));
    }

    private void attachAppender() {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private List<String> loggedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
