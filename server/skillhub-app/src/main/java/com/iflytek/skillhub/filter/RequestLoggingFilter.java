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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

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

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== HTTP Request ==========\n");
        sb.append("URL: ").append(request.getMethod()).append(" ").append(fullUrl).append("\n");
        sb.append("Remote Address: ").append(request.getRemoteAddr()).append("\n");
        sb.append("Headers: ").append(getHeaders(request)).append("\n");

        String requestBody = getRequestBody(request);
        if (requestBody != null && !requestBody.isBlank()) {
            sb.append("Request Body: ").append(requestBody).append("\n");
        }

        sb.append("Response Status: ").append(response.getStatus()).append("\n");
        sb.append("Duration: ").append(duration).append("ms\n");
        sb.append("===================================");

        log.info(sb.toString());
    }

    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        if (buf.length > 0) {
            try {
                return new String(buf, request.getCharacterEncoding());
            } catch (UnsupportedEncodingException e) {
                return "[unknown encoding]";
            }
        }
        return null;
    }
}
