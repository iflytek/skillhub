package com.iflytek.skillhub.exception;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.shared.exception.LocalizedMessage;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.security.SensitiveLogSanitizer;
import com.iflytek.skillhub.storage.StorageAccessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates application, domain, auth, and infrastructure exceptions into the platform's JSON API
 * error envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiResponseFactory apiResponseFactory;
    private final SensitiveLogSanitizer sensitiveLogSanitizer;
    private final SkillHubMetrics metrics;
    private final RequestIdAccessor requestIdAccessor;

    public GlobalExceptionHandler(ApiResponseFactory apiResponseFactory,
                                  SensitiveLogSanitizer sensitiveLogSanitizer,
                                  SkillHubMetrics metrics,
                                  RequestIdAccessor requestIdAccessor) {
        this.apiResponseFactory = apiResponseFactory;
        this.sensitiveLogSanitizer = sensitiveLogSanitizer;
        this.metrics = metrics;
        this.requestIdAccessor = requestIdAccessor;
    }

    @ExceptionHandler(LocalizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocalizedError(LocalizedException ex, HttpServletRequest request) {
        return renderLocalizedError(ex, ex.status(), request);
    }

    @ExceptionHandler(AuthFlowException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthFlowException(AuthFlowException ex, HttpServletRequest request) {
        return renderLocalizedError(ex, ex.getStatus(), request);
    }

    @ExceptionHandler(LocalizedDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocalizedDomainException(LocalizedDomainException ex, HttpServletRequest request) {
        return renderLocalizedError(ex, HttpStatus.valueOf(ex.statusCode()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElseGet(() -> ex.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse(null));
        logHandledException(HttpStatus.BAD_REQUEST, "validation.request.invalid", request);
        if (msg == null || msg.isBlank()) {
            return ResponseEntity.badRequest().body(apiResponseFactory.error(400, "error.badRequest"));
        }
        return ResponseEntity.badRequest().body(apiResponseFactory.errorMessage(400, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        logHandledException(HttpStatus.BAD_REQUEST, "error.badRequest", request);
        return ResponseEntity.badRequest().body(
                apiResponseFactory.error(400, "error.badRequest"));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMvcBadRequest(Exception ex, HttpServletRequest request) {
        logHandledException(HttpStatus.BAD_REQUEST, "error.badRequest", request);
        return ResponseEntity.badRequest().body(
                apiResponseFactory.error(400, "error.badRequest"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        logHandledException(HttpStatus.METHOD_NOT_ALLOWED, "error.methodNotAllowed", request);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supportedMethods = ex.getSupportedHttpMethods();
        if (supportedMethods != null && !supportedMethods.isEmpty()) {
            response.allow(supportedMethods.toArray(HttpMethod[]::new));
        }
        return response.body(apiResponseFactory.error(405, "error.methodNotAllowed"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        logHandledException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.unsupportedMediaType", request);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                apiResponseFactory.error(415, "error.unsupportedMediaType"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {
        logHandledException(HttpStatus.NOT_ACCEPTABLE, "error.notAcceptable", request);
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(
                apiResponseFactory.error(406, "error.notAcceptable"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(SecurityException ex, HttpServletRequest request) {
        logHandledException(HttpStatus.FORBIDDEN, "error.forbidden", request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                apiResponseFactory.error(403, "error.forbidden"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        logHandledException(HttpStatus.FORBIDDEN, "error.forbidden", request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                apiResponseFactory.error(403, "error.forbidden"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionInvalidated(
            IllegalStateException ex, HttpServletRequest request) {
        if (ex.getMessage() != null && ex.getMessage().contains("Session was invalidated")) {
            logHandledException(HttpStatus.UNAUTHORIZED, "error.session.expired", request);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(apiResponseFactory.error(401, "error.session.expired"));
        }
        throw ex;
    }

    @ExceptionHandler(StorageAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageAccess(StorageAccessException ex, HttpServletRequest request) {
        metrics.incrementStorageAccessFailure(ex.getOperation());
        logger.warn(
                "Object storage unavailable [requestId={}, method={}, path={}, authentication={}, operation={}, key={}]",
                requestIdAccessor.current(),
                request.getMethod(),
                sensitiveLogSanitizer.sanitizeRequestTarget(request),
                resolveAuthenticationState(request),
                ex.getOperation(),
                ex.getKey(),
                ex
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                apiResponseFactory.error(503, "error.storage.unavailable"));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<?> handleAsyncRequestTimeout(AsyncRequestTimeoutException ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && path.endsWith("/sse")) {
            logger.debug("SSE timeout [requestId={}, path={}]", requestIdAccessor.current(), path);
            return ResponseEntity.noContent().build();
        }

        logHandledException(HttpStatus.REQUEST_TIMEOUT, "error.request.timeout", request);
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(
                apiResponseFactory.error(408, "error.request.timeout"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex, HttpServletRequest request) {
        logger.error(
                "Unhandled API exception [requestId={}, method={}, path={}, authentication={}]",
                requestIdAccessor.current(),
                request.getMethod(),
                sensitiveLogSanitizer.sanitizeRequestTarget(request),
                resolveAuthenticationState(request),
                ex
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                apiResponseFactory.error(500, "error.internal"));
    }

    private void logHandledException(HttpStatus status, String messageCode, HttpServletRequest request) {
        logger.info(
                "API request failed [requestId={}, status={}, method={}, path={}, authentication={}, code={}]",
                requestIdAccessor.current(),
                status.value(),
                request.getMethod(),
                sensitiveLogSanitizer.sanitizeRequestTarget(request),
                resolveAuthenticationState(request),
                messageCode
        );
    }

    private ResponseEntity<ApiResponse<Void>> renderLocalizedError(LocalizedMessage error,
                                                                   HttpStatus status,
                                                                   HttpServletRequest request) {
        logHandledException(status, error.messageCode(), request);
        return ResponseEntity.status(status).body(
                apiResponseFactory.error(status.value(), error.messageCode(), error.messageArgs()));
    }

    private String resolveAuthenticationState(HttpServletRequest request) {
        if (request.getUserPrincipal() instanceof Authentication authentication
                && authentication.isAuthenticated()) {
            return "authenticated";
        }
        return "anonymous";
    }
}
