package com.iflytek.skillhub.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.security.SensitiveLogSanitizer;
import com.iflytek.skillhub.storage.StorageAccessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String STABLE_USER_ID = "stable-user-123";

    private final Logger logger =
            (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Mock
    private SensitiveLogSanitizer sensitiveLogSanitizer;

    @Mock
    private SkillHubMetrics metrics;

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;
    private ListAppender<ILoggingEvent> appender;
    private RequestIdAccessor requestIdAccessor;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.request.timeout", java.util.Locale.getDefault(), "Request timed out");
        messageSource.addMessage("error.badRequest", java.util.Locale.getDefault(), "Invalid request");
        messageSource.addMessage("error.methodNotAllowed", java.util.Locale.getDefault(), "HTTP method is not supported");
        messageSource.addMessage("error.unsupportedMediaType", java.util.Locale.getDefault(), "Unsupported media type");
        messageSource.addMessage("error.notAcceptable", java.util.Locale.getDefault(),
                "Requested response media type is not acceptable");
        requestIdAccessor = new RequestIdAccessor();
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-20T00:00:00Z"), ZoneOffset.UTC),
                requestIdAccessor
        );
        handler = new GlobalExceptionHandler(
                responseFactory,
                sensitiveLogSanitizer,
                metrics,
                requestIdAccessor
        );
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void handleAsyncRequestTimeout_shouldReturnNoContentForSseRequests() {
        when(request.getRequestURI()).thenReturn("/api/v1/notifications/sse");

        ResponseEntity<?> response = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void handleAsyncRequestTimeout_shouldReturnApiEnvelopeForNonSseRequests() {
        attachAppender();
        when(request.getRequestURI()).thenReturn("/api/v1/publish");
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/publish");

        ResponseEntity<?> response = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.code()).isEqualTo(408);
        assertThat(body.msg()).isEqualTo("Request timed out");
        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message)
                .contains("authentication=anonymous")
                .doesNotContain("userId="));
    }

    @Test
    void handleGlobalException_shouldLogAuthenticationWithoutStableUserId() {
        authenticateRequest();
        attachAppender();
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request))
                .thenReturn("/api/v1/skills/sensitive");

        try (RequestIdAccessor.Scope ignored = requestIdAccessor.open("request-123")) {
            ResponseEntity<ApiResponse<Void>> response = handler.handleGlobalException(
                    new RuntimeException("boom"), request);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message)
                .contains("requestId=request-123")
                .contains("authentication=authenticated")
                .doesNotContain(STABLE_USER_ID)
                .doesNotContain("userId="));
    }

    @Test
    void handleStorageAccess_shouldLogAuthenticationWithoutStableUserId() {
        authenticateRequest();
        attachAppender();
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request))
                .thenReturn("/api/v1/skills/test/download");

        StorageAccessException exception = new StorageAccessException(
                "download", "skills/test.zip", new RuntimeException("unavailable"));
        try (RequestIdAccessor.Scope ignored = requestIdAccessor.open("request-123")) {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleStorageAccess(exception, request);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message)
                .contains("requestId=request-123")
                .contains("authentication=authenticated")
                .doesNotContain(STABLE_USER_ID)
                .doesNotContain("userId="));
    }

    @Test
    void handleSessionInvalidated_shouldReturn401ForSessionException() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");

        IllegalStateException ex = new IllegalStateException("Session was invalidated");
        ResponseEntity<ApiResponse<Void>> response = handler.handleSessionInvalidated(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(401);
    }

    @Test
    void handleSessionInvalidated_shouldRethrowNonSessionException() {
        IllegalStateException ex = new IllegalStateException("Some other error");

        assertThatThrownBy(() -> handler.handleSessionInvalidated(ex, request))
                .isSameAs(ex);
    }

    @Test
    void handleMvcBadRequest_shouldReturn400WithoutUnhandledErrorLog() {
        attachAppender();
        prepareClientErrorRequest("POST", "/api/v1/skills?bad=value");

        List<Exception> exceptions = List.of(
                new MissingServletRequestParameterException("namespace", "String"),
                new HttpMessageNotReadableException("Malformed request body"),
                new MethodArgumentTypeMismatchException(
                        "bad-value", Long.class, "id", null, new NumberFormatException("bad-value"))
        );

        for (Exception exception : exceptions) {
            ResponseEntity<ApiResponse<Void>> response = handler.handleMvcBadRequest(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo(400);
        }
        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message)
                .contains("status=400")
                .contains("code=error.badRequest")
                .doesNotContain("Unhandled API exception"));
    }

    @Test
    void handleMethodNotAllowed_shouldReturn405AndAllowHeader() {
        attachAppender();
        prepareClientErrorRequest("DELETE", "/api/v1/skills/demo");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(405);
        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message)
                .contains("status=405")
                .contains("code=error.methodNotAllowed"));
    }

    @Test
    void handleUnsupportedMediaType_shouldReturn415() {
        attachAppender();
        prepareClientErrorRequest("POST", "/api/v1/skills");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_XML,
                        List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(415);
        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message)
                .contains("status=415")
                .contains("code=error.unsupportedMediaType"));
    }

    @Test
    void handleNotAcceptable_shouldReturn406() {
        attachAppender();
        prepareClientErrorRequest("GET", "/api/v1/skills");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(406);
        assertThat(loggedMessages()).anySatisfy(message -> assertThat(message)
                .contains("status=406")
                .contains("code=error.notAcceptable"));
    }

    private void authenticateRequest() {
        PlatformPrincipal principal = new PlatformPrincipal(
                STABLE_USER_ID,
                "User",
                "user@example.com",
                null,
                "local",
                Set.of("USER")
        );
        when(request.getUserPrincipal()).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private void prepareClientErrorRequest(String method, String sanitizedTarget) {
        when(request.getMethod()).thenReturn(method);
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn(sanitizedTarget);
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
