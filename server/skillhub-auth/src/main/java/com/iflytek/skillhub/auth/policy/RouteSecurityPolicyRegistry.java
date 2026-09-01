package com.iflytek.skillhub.auth.policy;

import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Authoritative route-policy catalog shared by web security configuration,
 * API-token scope checks, and request-context projection.
 */
@Component
public class RouteSecurityPolicyRegistry {

    private static final List<RouteAuthorizationPolicy> AUTHORIZATION_POLICIES = List.of(
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/health"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/search"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/resolve/**"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/download/**"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/providers"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/methods"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/me"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/session/bootstrap"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/direct/login"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/local/**"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/device/**"),
            RouteAuthorizationPolicy.permitAll(null, "/api/v1/check"),
            RouteAuthorizationPolicy.permitAll(null, "/actuator/health"),
            RouteAuthorizationPolicy.permitAll(null, "/v3/api-docs/**"),
            RouteAuthorizationPolicy.permitAll(null, "/swagger-ui/**"),
            RouteAuthorizationPolicy.permitAll(null, "/.well-known/**"),
            RouteAuthorizationPolicy.roles(null, "/actuator/prometheus", "SUPER_ADMIN", "AUDITOR"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/skills/*/star"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.PUT, "/api/v1/skills/*/star"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/v1/skills/*/star"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/skills/*/rating"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.PUT, "/api/v1/skills/*/rating"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/reviews"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/skills/*/reviews/me"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.PUT, "/api/v1/skills/*/reviews/me"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/v1/skills/*/reviews/me"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/web/skills/*/star"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.PUT, "/api/web/skills/*/star"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/web/skills/*/star"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/web/skills/*/rating"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.PUT, "/api/web/skills/*/rating"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/reviews"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/web/skills/*/reviews/me"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.PUT, "/api/web/skills/*/reviews/me"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/web/skills/*/reviews/me"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/versions"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/versions/*"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/versions/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/versions/*/files"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/versions/*/file"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/resolve"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/tags"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/labels"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/tags/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/tags/*/files"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/tags/*/file"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/labels"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/versions"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/versions/*"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/versions/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/versions/*/files"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/versions/*/file"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/resolve"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/tags"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/labels"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/tags/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/tags/*/files"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/tags/*/file"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/labels"),
            RouteAuthorizationPolicy.roles(HttpMethod.DELETE, "/api/v1/skills/id/*", "SUPER_ADMIN"),
            RouteAuthorizationPolicy.roles(HttpMethod.DELETE, "/api/v1/skills/*/*", "SUPER_ADMIN"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/web/skills/id/*"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/web/skills/*/*"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/namespaces"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/namespaces/*"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/web/namespaces"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/web/namespaces/*"),
            RouteAuthorizationPolicy.authenticated(null, "/api/v1/admin/**"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/cli/v1/auth/whoami"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/cli/v1/namespaces/*/skills"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/cli/v1/skills/search"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/cli/v1/skills/*/*/resolve"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/cli/v1/skills/*/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/cli/v1/skills/*/*/versions/*/download"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/cli/v1/skills/*/*"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.POST, "/api/cli/v1/skills/*/publish"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.POST, "/api/cli/v1/skills/*/publish/validate")
    );

    private static final List<ApiTokenPolicy> API_TOKEN_POLICIES = List.of(
            ApiTokenPolicy.allow(null, "/api/v1/health"),
            ApiTokenPolicy.allow(null, "/api/v1/auth/providers"),
            ApiTokenPolicy.allow(null, "/api/v1/auth/methods"),
            ApiTokenPolicy.allow(null, "/api/v1/auth/me"),
            ApiTokenPolicy.allow(null, "/api/v1/auth/device/**"),
            ApiTokenPolicy.allow(null, "/api/v1/check"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/whoami"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/search"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/skills"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/skills/**"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/skills"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/skills/**"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/labels"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/labels"),
            ApiTokenPolicy.allow(HttpMethod.PUT, "/api/v1/skills/*/star"),
            ApiTokenPolicy.allow(HttpMethod.DELETE, "/api/v1/skills/*/star"),
            ApiTokenPolicy.allow(HttpMethod.PUT, "/api/v1/skills/*/rating"),
            ApiTokenPolicy.allow(HttpMethod.PUT, "/api/v1/skills/*/reviews/me"),
            ApiTokenPolicy.allow(HttpMethod.DELETE, "/api/v1/skills/*/reviews/me"),
            ApiTokenPolicy.allow(HttpMethod.PUT, "/api/web/skills/*/star"),
            ApiTokenPolicy.allow(HttpMethod.DELETE, "/api/web/skills/*/star"),
            ApiTokenPolicy.allow(HttpMethod.PUT, "/api/web/skills/*/rating"),
            ApiTokenPolicy.allow(HttpMethod.PUT, "/api/web/skills/*/reviews/me"),
            ApiTokenPolicy.allow(HttpMethod.DELETE, "/api/web/skills/*/reviews/me"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/namespaces"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/namespaces/*"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/namespaces"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/namespaces/*"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/resolve/**"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/download"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/download/**"),
            ApiTokenPolicy.allow(null, "/.well-known/**"),
            ApiTokenPolicy.allow(null, "/actuator/health"),
            ApiTokenPolicy.allow(null, "/v3/api-docs/**"),
            ApiTokenPolicy.allow(null, "/swagger-ui/**"),
            ApiTokenPolicy.require(null, "/api/v1/tokens", "token:manage"),
            ApiTokenPolicy.require(null, "/api/v1/tokens/**", "token:manage"),
            ApiTokenPolicy.require(HttpMethod.DELETE, "/api/v1/skills/id/*", "skill:delete"),
            ApiTokenPolicy.require(HttpMethod.DELETE, "/api/v1/skills/*/*", "skill:delete"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/v1/skills", "skill:publish"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/v1/skills/*/publish", "skill:publish"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/web/skills/*/publish", "skill:publish"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/v1/publish", "skill:publish"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/cli/v1/auth/whoami"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/cli/v1/namespaces/*/skills"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/cli/v1/skills/search"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/cli/v1/skills/*/*/resolve"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/cli/v1/skills/*/*/download"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/cli/v1/skills/*/*/versions/*/download"),
            ApiTokenPolicy.require(HttpMethod.DELETE, "/api/cli/v1/skills/*/*", "skill:delete"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/cli/v1/skills/*/publish", "skill:publish"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/cli/v1/skills/*/publish/validate", "skill:publish")
    );

    /**
     * Authorization routes that intentionally have no API-token counterpart, keyed as
     * {@code "<METHOD|ANY> <pattern>"} to match {@link #routeKey(HttpMethod, String)}.
     *
     * <p>These are browser-session surfaces: the interactive login flows, the admin console,
     * and skill deletion through the web surface, which goes through {@code /api/v1} or
     * {@code /api/cli/v1} with the {@code skill:delete} scope instead. Bearer tokens are
     * deliberately rejected on exactly these routes and nowhere else &mdash; anything else the
     * authorization list opens must also be reachable with a token holding the required scope.</p>
     */
    private static final Set<String> SESSION_ONLY_ROUTES = Set.of(
            "ANY /api/v1/auth/session/bootstrap",
            "ANY /api/v1/auth/direct/login",
            "ANY /api/v1/auth/local/**",
            "ANY /api/v1/admin/**",
            "DELETE /api/web/skills/id/*",
            "DELETE /api/web/skills/*/*"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public List<RouteAuthorizationPolicy> authorizationPolicies() {
        return AUTHORIZATION_POLICIES;
    }

    /**
     * Resolves the first matching authorization policy for a request. Routes not
     * listed in the catalog follow Spring Security's authenticated fallback.
     */
    public AccessLevel accessLevel(String method, String path) {
        if (path == null) {
            return AccessLevel.AUTHENTICATED;
        }
        return AUTHORIZATION_POLICIES.stream()
                .filter(policy -> policy.matches(method, path, pathMatcher))
                .map(RouteAuthorizationPolicy::accessLevel)
                .findFirst()
                .orElse(AccessLevel.AUTHENTICATED);
    }

    /**
     * Authorization routes that are deliberately unreachable with an API token.
     */
    public Set<String> sessionOnlyRoutes() {
        return SESSION_ONLY_ROUTES;
    }

    /**
     * Stable key for a route in the authorization list, used to pair it with
     * {@link #sessionOnlyRoutes()}.
     */
    public static String routeKey(HttpMethod method, String pattern) {
        return (method == null ? "ANY" : method.name()) + " " + pattern;
    }

    /**
     * Returns the application-relative request path used by security policies.
     *
     * <p>When a reverse proxy supplies {@code X-Forwarded-Prefix}, Spring exposes
     * that external prefix through {@code getRequestURI()} while keeping the
     * application route in {@code getServletPath()}. Security filters must match
     * the latter or bearer authentication is skipped for sub-path deployments.</p>
     */
    public static String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        return request.getRequestURI();
    }

    public ApiTokenAuthorizationDecision authorizeApiToken(String method, String path, Set<String> tokenScopes) {
        if (!isApiPath(path)) {
            return ApiTokenAuthorizationDecision.allow();
        }

        for (ApiTokenPolicy policy : API_TOKEN_POLICIES) {
            if (!policy.matches(method, path, pathMatcher)) {
                continue;
            }
            if (policy.requiredScope() == null || tokenScopes.contains(policy.requiredScope())) {
                return ApiTokenAuthorizationDecision.allow();
            }
            return ApiTokenAuthorizationDecision.missingScope(policy.requiredScope());
        }

        return ApiTokenAuthorizationDecision.unsupported(path);
    }

    public boolean shouldIgnoreCsrf(String method, String path, String authorizationHeader) {
        return shouldIgnoreCsrf(method, path, authorizationHeader, false);
    }

    public boolean shouldIgnoreCsrf(String method, String path, String authorizationHeader, boolean hasSessionCookie) {
        if (!hasSessionCookie && authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return true;
        }
        if (path == null) {
            return false;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return "/api/v1/auth/device/code".equals(path)
                || "/api/v1/auth/device/token".equals(path);
    }

    public boolean shouldProjectRequestContext(String path) {
        return path != null && path.startsWith("/api/");
    }

    private boolean isApiPath(String path) {
        return shouldProjectRequestContext(path);
    }

    public record ApiTokenAuthorizationDecision(boolean allowed, String requiredScope, String message) {
        public static ApiTokenAuthorizationDecision allow() {
            return new ApiTokenAuthorizationDecision(true, null, null);
        }

        public static ApiTokenAuthorizationDecision missingScope(String requiredScope) {
            return new ApiTokenAuthorizationDecision(false, requiredScope, "Missing API token scope: " + requiredScope);
        }

        public static ApiTokenAuthorizationDecision unsupported(String path) {
            return new ApiTokenAuthorizationDecision(false, null, "API token cannot access endpoint: " + path);
        }
    }

    public enum AccessLevel {
        PERMIT_ALL,
        AUTHENTICATED,
        ROLE_PROTECTED
    }

    public record RouteAuthorizationPolicy(HttpMethod method, String pattern, AccessLevel accessLevel, String[] roles) {
        public static RouteAuthorizationPolicy permitAll(HttpMethod method, String pattern) {
            return new RouteAuthorizationPolicy(method, pattern, AccessLevel.PERMIT_ALL, new String[0]);
        }

        public static RouteAuthorizationPolicy authenticated(HttpMethod method, String pattern) {
            return new RouteAuthorizationPolicy(method, pattern, AccessLevel.AUTHENTICATED, new String[0]);
        }

        public static RouteAuthorizationPolicy roles(HttpMethod method, String pattern, String... roles) {
            return new RouteAuthorizationPolicy(method, pattern, AccessLevel.ROLE_PROTECTED, roles);
        }

        public RequestMatcher toRequestMatcher() {
            return method == null
                    ? new AntPathRequestMatcher(pattern)
                    : new AntPathRequestMatcher(pattern, method.name());
        }

        boolean matches(String requestMethod, String requestPath, AntPathMatcher matcher) {
            if (method != null && (requestMethod == null || !method.name().equalsIgnoreCase(requestMethod))) {
                return false;
            }
            return matcher.match(pattern, requestPath);
        }
    }

    private record ApiTokenPolicy(HttpMethod method, String pattern, String requiredScope) {
        static ApiTokenPolicy allow(HttpMethod method, String pattern) {
            return new ApiTokenPolicy(method, pattern, null);
        }

        static ApiTokenPolicy require(HttpMethod method, String pattern, String requiredScope) {
            return new ApiTokenPolicy(method, pattern, requiredScope);
        }

        boolean matches(String requestMethod, String requestPath, AntPathMatcher matcher) {
            if (method != null && (requestMethod == null || !method.name().equalsIgnoreCase(requestMethod))) {
                return false;
            }
            return matcher.match(pattern, requestPath);
        }
    }
}
