package com.iflytek.skillhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Converts authorization failures on API routes into the platform's standard JSON error envelope.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiAccessDeniedHandler.class);
    private final ObjectMapper objectMapper;
    private final ApiResponseFactory apiResponseFactory;
    private final SensitiveLogSanitizer sensitiveLogSanitizer;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper,
                                  ApiResponseFactory apiResponseFactory,
                                  SensitiveLogSanitizer sensitiveLogSanitizer) {
        this.objectMapper = objectMapper;
        this.apiResponseFactory = apiResponseFactory;
        this.sensitiveLogSanitizer = sensitiveLogSanitizer;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String reason = accessDeniedException.getMessage();
        logger.info(
                "Forbidden API request [requestId={}, method={}, path={}, reason={}, detail={}]",
                MDC.get("requestId"),
                request.getMethod(),
                sensitiveLogSanitizer.sanitizeRequestTarget(request),
                accessDeniedException.getClass().getSimpleName(),
                reason
        );
        // Callers previously saw a bare "Forbidden" whether their token lacked
        // a scope, the endpoint was closed to API tokens, or the path simply
        // did not exist — so clients guess, and their guesses mislead. The
        // filters already compute an exact reason; pass it through.
        ApiResponse<Void> body = reason != null && !reason.isBlank()
                ? apiResponseFactory.error(403, "error.forbidden.detail", reason)
                : apiResponseFactory.error(403, "error.forbidden");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
