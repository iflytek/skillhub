package com.iflytek.skillhub.auth.policy;

import java.util.List;
import java.util.Set;
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
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/skills/*/rating"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/web/skills/*/star"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/web/skills/*/rating"),
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
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/tags/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/tags/*/files"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/skills/*/*/tags/*/file"),
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
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/tags/*/download"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/tags/*/files"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/skills/*/*/tags/*/file"),
            RouteAuthorizationPolicy.roles(HttpMethod.DELETE, "/api/v1/skills/*/*", "SUPER_ADMIN"),
            RouteAuthorizationPolicy.authenticated(HttpMethod.DELETE, "/api/web/skills/*/*"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/namespaces"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/namespaces/*"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/namespaces"),
            RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/namespaces/*"),
            RouteAuthorizationPolicy.authenticated(null, "/api/v1/admin/**")
    );

    private static final List<ApiTokenPolicy> API_TOKEN_POLICIES = List.of(
            ApiTokenPolicy.allow(null, "/api/v1/health"),
            ApiTokenPolicy.allow(null, "/api/v1/auth/providers"),
            ApiTokenPolicy.allow(null, "/api/v1/auth/me"),
            ApiTokenPolicy.allow(null, "/api/v1/auth/device/**"),
            ApiTokenPolicy.allow(null, "/api/v1/check"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/whoami"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/search"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/skills"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/skills/**"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/skills"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/skills/**"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/namespaces"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/namespaces/*"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/namespaces"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/web/namespaces/*"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/resolve/**"),
            ApiTokenPolicy.allow(HttpMethod.GET, "/api/v1/download"),
            ApiTokenPolicy.allow(null, "/.well-known/**"),
            ApiTokenPolicy.allow(null, "/actuator/health"),
            ApiTokenPolicy.allow(null, "/v3/api-docs/**"),
            ApiTokenPolicy.allow(null, "/swagger-ui/**"),
            ApiTokenPolicy.require(null, "/api/v1/tokens", "token:manage"),
            ApiTokenPolicy.require(null, "/api/v1/tokens/**", "token:manage"),
            ApiTokenPolicy.require(HttpMethod.DELETE, "/api/v1/skills/*/*", "skill:delete"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/v1/skills", "skill:publish"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/v1/skills/*/publish", "skill:publish"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/web/skills/*/publish", "skill:publish"),
            ApiTokenPolicy.require(HttpMethod.POST, "/api/v1/publish", "skill:publish")
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public List<RouteAuthorizationPolicy> authorizationPolicies() {
        return AUTHORIZATION_POLICIES;
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

    public boolean shouldIgnoreCsrf(String path, String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return true;
        }
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/");
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
