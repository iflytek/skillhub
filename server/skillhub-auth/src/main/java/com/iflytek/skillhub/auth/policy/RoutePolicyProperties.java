package com.iflytek.skillhub.auth.policy;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Optional configuration overlay for environment-specific route authorization rules.
 *
 * <p>Core platform policies remain in code. These properties only append extra
 * rules without weakening the audited defaults.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.security.route-policy")
public class RoutePolicyProperties {

    private List<RouteRule> extraPermitAll = List.of();
    private List<RouteRule> extraAuthenticated = List.of();
    private List<RoleProtectedRouteRule> extraRoleProtected = List.of();

    public List<RouteRule> getExtraPermitAll() {
        return extraPermitAll;
    }

    public void setExtraPermitAll(List<RouteRule> extraPermitAll) {
        this.extraPermitAll = extraPermitAll == null ? List.of() : List.copyOf(extraPermitAll);
    }

    public List<RouteRule> getExtraAuthenticated() {
        return extraAuthenticated;
    }

    public void setExtraAuthenticated(List<RouteRule> extraAuthenticated) {
        this.extraAuthenticated = extraAuthenticated == null ? List.of() : List.copyOf(extraAuthenticated);
    }

    public List<RoleProtectedRouteRule> getExtraRoleProtected() {
        return extraRoleProtected;
    }

    public void setExtraRoleProtected(List<RoleProtectedRouteRule> extraRoleProtected) {
        this.extraRoleProtected = extraRoleProtected == null ? List.of() : List.copyOf(extraRoleProtected);
    }

    public static class RouteRule {
        private HttpMethod method;
        private String pattern;

        public HttpMethod getMethod() {
            return method;
        }

        public void setMethod(HttpMethod method) {
            this.method = method;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }

    public static class RoleProtectedRouteRule extends RouteRule {
        private List<String> roles = List.of();

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
