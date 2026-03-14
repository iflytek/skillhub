package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ApiTokenScopeService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final List<ScopeRule> UNSCOPED_ALLOWED_RULES = List.of(
        ScopeRule.allow(null, "/api/v1/health"),
        ScopeRule.allow(null, "/api/v1/auth/providers"),
        ScopeRule.allow(null, "/api/v1/auth/me"),
        ScopeRule.allow(null, "/api/v1/auth/device/**"),
        ScopeRule.allow(null, "/api/v1/check"),
        ScopeRule.allow("GET", "/api/v1/whoami"),
        ScopeRule.allow("GET", "/api/v1/skills"),
        ScopeRule.allow("GET", "/api/v1/skills/**"),
        ScopeRule.allow("GET", "/api/v1/namespaces"),
        ScopeRule.allow("GET", "/api/v1/namespaces/*"),
        ScopeRule.allow("GET", "/api/compat/v1/search"),
        ScopeRule.allow("GET", "/api/compat/v1/resolve/**"),
        ScopeRule.allow("GET", "/api/compat/v1/whoami"),
        ScopeRule.allow(null, "/.well-known/**"),
        ScopeRule.allow(null, "/actuator/health"),
        ScopeRule.allow(null, "/v3/api-docs/**"),
        ScopeRule.allow(null, "/swagger-ui/**")
    );

    private static final List<ScopeRule> REQUIRED_SCOPE_RULES = List.of(
        ScopeRule.require(null, "/api/v1/tokens", "token:manage"),
        ScopeRule.require(null, "/api/v1/tokens/**", "token:manage"),
        ScopeRule.require("POST", "/api/v1/skills/*/publish", "skill:publish"),
        ScopeRule.require("POST", "/api/v1/publish", "skill:publish"),
        ScopeRule.require("POST", "/api/compat/v1/publish", "skill:publish")
    );

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiTokenScopeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Set<String> parseScopes(String scopeJson) {
        if (scopeJson == null || scopeJson.isBlank()) {
            return Set.of();
        }

        try {
            List<String> scopes = objectMapper.readValue(scopeJson, STRING_LIST);
            Set<String> normalized = new LinkedHashSet<>();
            for (String scope : scopes) {
                if (scope != null) {
                    String trimmed = scope.trim();
                    if (!trimmed.isEmpty()) {
                        normalized.add(trimmed);
                    }
                }
            }
            return Set.copyOf(normalized);
        } catch (Exception e) {
            return Set.of();
        }
    }

    public AuthorizationDecision authorize(String method, String path, Set<String> tokenScopes) {
        if (!isApiPath(path)) {
            return AuthorizationDecision.allow();
        }

        for (ScopeRule rule : UNSCOPED_ALLOWED_RULES) {
            if (rule.matches(method, path, pathMatcher)) {
                return AuthorizationDecision.allow();
            }
        }

        for (ScopeRule rule : REQUIRED_SCOPE_RULES) {
            if (rule.matches(method, path, pathMatcher)) {
                if (tokenScopes.contains(rule.requiredScope())) {
                    return AuthorizationDecision.allow();
                }
                return AuthorizationDecision.missingScope(rule.requiredScope());
            }
        }

        return AuthorizationDecision.unsupported(path);
    }

    private boolean isApiPath(String path) {
        return path != null && (path.startsWith("/api/v1/") || path.startsWith("/api/compat/"));
    }

    public record AuthorizationDecision(boolean allowed, String requiredScope, String message) {
        public static AuthorizationDecision allow() {
            return new AuthorizationDecision(true, null, null);
        }

        public static AuthorizationDecision missingScope(String requiredScope) {
            return new AuthorizationDecision(false, requiredScope, "Missing API token scope: " + requiredScope);
        }

        public static AuthorizationDecision unsupported(String path) {
            return new AuthorizationDecision(false, null, "API token cannot access endpoint: " + path);
        }
    }

    private record ScopeRule(String method, String pattern, String requiredScope) {
        static ScopeRule allow(String method, String pattern) {
            return new ScopeRule(method, pattern, null);
        }

        static ScopeRule require(String method, String pattern, String requiredScope) {
            return new ScopeRule(method, pattern, requiredScope);
        }

        boolean matches(String requestMethod, String requestPath, AntPathMatcher matcher) {
            if (method != null && !method.equalsIgnoreCase(requestMethod)) {
                return false;
            }
            return matcher.match(pattern, requestPath);
        }
    }
}
