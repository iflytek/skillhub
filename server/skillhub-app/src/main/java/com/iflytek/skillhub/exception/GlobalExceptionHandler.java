package com.iflytek.skillhub.exception;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<Void>> handleLocalizedError(LocalizedException ex) {
        HttpStatus status = ex.status();
        return ResponseEntity.status(status).body(
            apiResponseFactory.error(status.value(), ex.messageCode(), ex.messageArgs()));
    }

    @ExceptionHandler(DomainBadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainBadRequest(DomainBadRequestException ex) {
        return ResponseEntity.badRequest().body(
                apiResponseFactory.error(400, ex.messageCode(), ex.messageArgs()));
    }

    @ExceptionHandler(DomainForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainForbidden(DomainForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                apiResponseFactory.error(403, ex.messageCode(), ex.messageArgs()));
    }

    @ExceptionHandler(DomainNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainNotFound(DomainNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                apiResponseFactory.error(404, ex.messageCode(), ex.messageArgs()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElseGet(() -> ex.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse(null));
        if (msg == null || msg.isBlank()) {
            return ResponseEntity.badRequest().body(apiResponseFactory.error(400, "error.badRequest"));
        }
        return ResponseEntity.badRequest().body(apiResponseFactory.errorMessage(400, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                apiResponseFactory.error(400, "error.badRequest"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                apiResponseFactory.error(403, "error.forbidden"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
        String requestId = MDC.get("requestId");
        logger.error("Unhandled exception [requestId={}]", requestId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            apiResponseFactory.error(500, "error.internal"));
    }
}
