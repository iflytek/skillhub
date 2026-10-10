package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses token scopes and evaluates whether a token may access a given HTTP
 * method and path combination.
 */
@Service
public class ApiTokenScopeService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    public ApiTokenScopeService(ObjectMapper objectMapper,
                                RouteSecurityPolicyRegistry routeSecurityPolicyRegistry) {
        this.objectMapper = objectMapper;
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
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
        RouteSecurityPolicyRegistry.ApiTokenAuthorizationDecision decision =
                routeSecurityPolicyRegistry.authorizeApiToken(method, path, tokenScopes);
        if (decision.allowed()) {
            return AuthorizationDecision.allow();
        }
        if (decision.requiredScope() != null) {
            return AuthorizationDecision.missingScope(decision.requiredScope());
        }
        return AuthorizationDecision.unsupported(path);
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
}
