package com.iflytek.skillhub.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Transport-level audit fields extracted from the current HTTP request.
 */
public record AuditRequestContext(
        String clientIp,
        String userAgent
) {
    public static AuditRequestContext from(HttpServletRequest request) {
        return new AuditRequestContext(
                request != null ? request.getRemoteAddr() : null,
                request != null ? request.getHeader("User-Agent") : null
        );
    }
}
