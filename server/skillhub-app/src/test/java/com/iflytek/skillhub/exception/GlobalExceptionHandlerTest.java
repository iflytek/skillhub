package com.iflytek.skillhub.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.security.SensitiveLogSanitizer;
import com.iflytek.skillhub.storage.StorageAccessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private SensitiveLogSanitizer sensitiveLogSanitizer;

    @Mock
    private SkillHubMetrics metrics;

    @Mock
    private HttpServletRequest request;

    @Mock
    private Authentication authentication;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.request.timeout", java.util.Locale.getDefault(), "Request timed out");
        messageSource.addMessage("error.badRequest", java.util.Locale.getDefault(), "Bad request");
        messageSource.addMessage("error.methodNotAllowed", java.util.Locale.getDefault(), "Method not allowed");
        messageSource.addMessage("error.notFound", java.util.Locale.getDefault(), "Not found");
        messageSource.addMessage("error.forbidden", java.util.Locale.getDefault(), "Forbidden");
        messageSource.addMessage("error.storage.unavailable", java.util.Locale.getDefault(), "Storage unavailable");
        messageSource.addMessage("error.request.timeout", java.util.Locale.getDefault(), "Request timed out");
        messageSource.addMessage("error.internal", java.util.Locale.getDefault(), "Internal error");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-20T00:00:00Z"), ZoneOffset.UTC)
        );
        handler = new GlobalExceptionHandler(responseFactory, sensitiveLogSanitizer, metrics);
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
        when(request.getRequestURI()).thenReturn("/api/v1/publish");
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/publish");

        ResponseEntity<?> response = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.code()).isEqualTo(408);
        assertThat(body.msg()).isEqualTo("Request timed out");
    }

    @Test
    void handleBadRequest_returns400() {
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/publish");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(
                new IllegalArgumentException("invalid"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(400);
    }

    @Test
    void handleMethodNotAllowed_returns405() {
        when(request.getMethod()).thenReturn("DELETE");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/publish");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo(405);
    }

    @Test
    void handleNotFound_returns404() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/missing");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/missing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(404);
    }

    @Test
    void handleForbidden_returns403() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");

        ResponseEntity<ApiResponse<Void>> response = handler.handleForbidden(
                new SecurityException("denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(403);
    }

    @Test
    void handleAccessDenied_returns403() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(
                new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(403);
    }

    @Test
    void handleStorageAccess_returns503() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills/global/test/download");
        when(request.getUserPrincipal()).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleStorageAccess(
                new StorageAccessException("getObject", "key", new RuntimeException()), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo(503);
    }

    @Test
    void handleGlobalException_returns500() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");
        when(request.getUserPrincipal()).thenReturn(null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleGlobalException(
                new RuntimeException("unexpected"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(500);
    }

    @Test
    void handleValidation_withFieldErrors_returns400() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");

        BindingResult bindingResult = new BeanPropertyBindingResult(new DummyDto(), "target");
        bindingResult.rejectValue("name", "error", "Name is required");
        MethodArgumentNotValidException ex = createMethodArgumentNotValidException(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(400);
    }

    @Test
    void handleValidation_withGlobalErrorsOnly_returns400() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");

        BindingResult bindingResult = new BeanPropertyBindingResult(new DummyDto(), "target");
        bindingResult.reject("global.error");
        MethodArgumentNotValidException ex = createMethodArgumentNotValidException(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(400);
    }

    @Test
    void handleValidation_withEmptyErrors_returns400() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");

        BindingResult bindingResult = new BeanPropertyBindingResult(new DummyDto(), "target");
        MethodArgumentNotValidException ex = createMethodArgumentNotValidException(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(400);
    }

    @Test
    void handleAuthFlowException_returnsCorrectStatus() {
        when(request.getMethod()).thenReturn("POST");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/login");

        AuthFlowException ex = new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.unauthorized");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthFlowException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo(401);
    }

    @Test
    void resolveUserId_withPlatformPrincipal_returnsUserId() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");
        PlatformPrincipal principal = new PlatformPrincipal("user-123", "test", "email@test.com", "avatar", "oauth", java.util.Set.of());
        when(request.getUserPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);

        ResponseEntity<ApiResponse<Void>> response = handler.handleForbidden(new SecurityException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resolveUserId_withNonPlatformPrincipal_returnsName() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");
        when(request.getUserPrincipal()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("anonymous");
        when(authentication.getName()).thenReturn("anonymous");

        ResponseEntity<ApiResponse<Void>> response = handler.handleForbidden(new SecurityException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleLocalizedDomainException_returnsCorrectStatus() {
        when(request.getMethod()).thenReturn("GET");
        when(sensitiveLogSanitizer.sanitizeRequestTarget(request)).thenReturn("/api/v1/skills");

        com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException ex =
                new com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException("error.notFound") {
                    @Override
                    public int statusCode() {
                        return 404;
                    }
                };

        ResponseEntity<ApiResponse<Void>> response = handler.handleLocalizedDomainException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(404);
    }

    private MethodArgumentNotValidException createMethodArgumentNotValidException(BindingResult bindingResult) throws Exception {
        java.lang.reflect.Method method = Object.class.getMethod("toString");
        MethodParameter parameter = new MethodParameter(method, -1);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    public static class DummyDto {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
