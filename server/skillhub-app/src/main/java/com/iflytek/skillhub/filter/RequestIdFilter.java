package com.iflytek.skillhub.filter;

import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every request has a request identifier for logs, responses, and downstream audit
 * correlation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RequestIdAccessor requestIdAccessor;

    public RequestIdFilter(RequestIdAccessor requestIdAccessor) {
        this.requestIdAccessor = requestIdAccessor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!RequestIdAccessor.isValid(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        response.setHeader(REQUEST_ID_HEADER, requestId);

        try (RequestIdAccessor.Scope ignored = requestIdAccessor.open(requestId)) {
            filterChain.doFilter(request, response);
        }
    }
}
