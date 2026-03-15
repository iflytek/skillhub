package com.iflytek.skillhub.exception;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiResponseFactory apiResponseFactory;

    public GlobalExceptionHandler(ApiResponseFactory apiResponseFactory) {
        this.apiResponseFactory = apiResponseFactory;
    }

    @ExceptionHandler(LocalizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocalizedError(LocalizedException ex, HttpServletRequest request) {
        HttpStatus status = ex.status();
        logHandledException(status, ex.messageCode(), request);
        return ResponseEntity.status(status).body(
            apiResponseFactory.error(status.value(), ex.messageCode(), ex.messageArgs()));
    }

    @ExceptionHandler(AuthFlowException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthFlowException(AuthFlowException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        logHandledException(status, ex.getMessageCode(), request);
        return ResponseEntity.status(status).body(
            apiResponseFactory.error(status.value(), ex.getMessageCode(), ex.getMessageArgs()));
    }

    @ExceptionHandler(DomainBadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainBadRequest(DomainBadRequestException ex, HttpServletRequest request) {
        logHandledException(HttpStatus.BAD_REQUEST, ex.messageCode(), request);
        return ResponseEntity.badRequest().body(
                apiResponseFactory.error(400, ex.messageCode(), ex.messageArgs()));
    }

    @ExceptionHandler(DomainForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainForbidden(DomainForbiddenException ex, HttpServletRequest request) {
        logHandledException(HttpStatus.FORBIDDEN, ex.messageCode(), request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                apiResponseFactory.error(403, ex.messageCode(), ex.messageArgs()));
    }

    @ExceptionHandler(DomainNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainNotFound(DomainNotFoundException ex, HttpServletRequest request) {
        logHandledException(HttpStatus.NOT_FOUND, ex.messageCode(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                apiResponseFactory.error(404, ex.messageCode(), ex.messageArgs()));
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex, HttpServletRequest request) {
        logger.error(
                "Unhandled API exception [requestId={}, method={}, path={}, userId={}]",
                MDC.get("requestId"),
                request.getMethod(),
                request.getRequestURI(),
                resolveUserId(request),
                ex
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            apiResponseFactory.error(500, "error.internal"));
    }

    private void logHandledException(HttpStatus status, String messageCode, HttpServletRequest request) {
        logger.info(
                "API request failed [requestId={}, status={}, method={}, path={}, userId={}, code={}]",
                MDC.get("requestId"),
                status.value(),
                request.getMethod(),
                request.getRequestURI(),
                resolveUserId(request),
                messageCode
        );
    }

    private String resolveUserId(HttpServletRequest request) {
        if (!(request.getUserPrincipal() instanceof Authentication authentication)) {
            return "anonymous";
        }
        if (authentication.getPrincipal() instanceof PlatformPrincipal principal) {
            return principal.userId();
        }
        return authentication.getName();
    }
}
