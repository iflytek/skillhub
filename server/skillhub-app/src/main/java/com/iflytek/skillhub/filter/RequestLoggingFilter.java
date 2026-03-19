package com.iflytek.skillhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;

/**
 * Logs inbound HTTP requests with only core parameters to keep log files compact.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int MAX_LOG_BODY_LENGTH = 200;

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/actuator", "/favicon.ico", "/assets/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequest(cachedRequest, cachedResponse, duration);
            cachedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long duration) {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? requestUri + "?" + queryString : requestUri;

        String contentType = request.getContentType();
        String userAgent = request.getHeader("User-Agent");

        StringBuilder sb = new StringBuilder();
        sb.append(request.getMethod()).append(" ").append(fullUrl);
        sb.append(" | ").append(response.getStatus());
        sb.append(" | ").append(duration).append("ms");
        sb.append(" | ").append(request.getRemoteAddr());
        if (contentType != null) {
            sb.append(" | Content-Type: ").append(contentType);
        }
        if (userAgent != null) {
            sb.append(" | UA: ").append(truncate(userAgent, 80));
        }

        String requestBody = getRequestBody(request);
        if (requestBody != null && !requestBody.isBlank()) {
            sb.append(" | Body: ").append(requestBody);
        }

        log.info(sb.toString());
    }

    private boolean shouldSkip(String uri) {
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        if (buf.length > 0) {
            try {
                return truncate(new String(buf, request.getCharacterEncoding()), MAX_LOG_BODY_LENGTH);
            } catch (UnsupportedEncodingException e) {
                return "[unknown encoding]";
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }
}
