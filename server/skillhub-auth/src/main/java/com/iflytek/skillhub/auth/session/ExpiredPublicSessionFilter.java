package com.iflytek.skillhub.auth.session;

import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Treats an expired session cookie as absent only for routes that already allow anonymous access.
 */
public final class ExpiredPublicSessionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ExpiredPublicSessionFilter.class);

    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;
    private final CorruptSessionRemover corruptSessionRemover;
    private final Set<String> sessionCookieNames;

    public ExpiredPublicSessionFilter(RouteSecurityPolicyRegistry routeSecurityPolicyRegistry,
                                      CorruptSessionRemover corruptSessionRemover,
                                      String sessionCookieName) {
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
        this.corruptSessionRemover = corruptSessionRemover;
        Set<String> cookieNames = new HashSet<>();
        cookieNames.add(sessionCookieName);
        cookieNames.add("JSESSIONID");
        this.sessionCookieNames = Set.copyOf(cookieNames);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestPath = RouteSecurityPolicyRegistry.requestPath(request);
        boolean publicRoute = routeSecurityPolicyRegistry.accessLevel(request.getMethod(), requestPath)
                == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL;
        try {
            String requestedSessionId = request.getRequestedSessionId();
            if (publicRoute && requestedSessionId != null && !request.isRequestedSessionIdValid()) {
                filterChain.doFilter(new SessionlessRequest(request, sessionCookieNames), response);
                return;
            }
        } catch (SerializationException error) {
            // Spring Session resolves and caches the server-side session id before loading the
            // Redis record. Re-read that cached id so custom cookie serializers and jvmRoute
            // settings remain Spring Session's responsibility.
            String corruptSessionId = request.getRequestedSessionId();
            if (corruptSessionId == null) {
                throw error;
            }
            // Delete only the unreadable record. Redis connectivity failures from deleteById
            // intentionally propagate instead of disguising a storage outage as logout.
            corruptSessionRemover.remove(corruptSessionId);
            expireSessionCookies(request, response);
            log.warn("Removed an unreadable HTTP session; the client must authenticate again");
            filterChain.doFilter(new SessionlessRequest(request, sessionCookieNames), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void expireSessionCookies(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        Arrays.stream(cookies)
                .filter(cookie -> sessionCookieNames.contains(cookie.getName()))
                .forEach(cookie -> {
                    Cookie expired = new Cookie(cookie.getName(), "");
                    expired.setHttpOnly(true);
                    expired.setSecure(request.isSecure());
                    expired.setPath(request.getContextPath().isBlank() ? "/" : request.getContextPath());
                    expired.setMaxAge(0);
                    response.addCookie(expired);
                });
    }

    private static final class SessionlessRequest extends HttpServletRequestWrapper {

        private final Set<String> sessionCookieNames;

        private SessionlessRequest(HttpServletRequest request, Set<String> sessionCookieNames) {
            super(request);
            this.sessionCookieNames = sessionCookieNames;
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public Cookie[] getCookies() {
            Cookie[] cookies = super.getCookies();
            if (cookies == null) {
                return null;
            }
            Cookie[] retained = Arrays.stream(cookies)
                    .filter(cookie -> !sessionCookieNames.contains(cookie.getName()))
                    .toArray(Cookie[]::new);
            return retained.length == 0 ? null : retained;
        }
    }
}
