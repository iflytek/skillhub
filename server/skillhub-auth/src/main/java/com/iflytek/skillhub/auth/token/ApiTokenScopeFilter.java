package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces fine-grained API token scopes after token authentication has established the principal.
 */
@Component
public class ApiTokenScopeFilter extends OncePerRequestFilter {

    private final ApiTokenScopeService apiTokenScopeService;
    private final AccessDeniedHandler accessDeniedHandler;

    public ApiTokenScopeFilter(ApiTokenScopeService apiTokenScopeService,
                               AccessDeniedHandler accessDeniedHandler) {
        this.apiTokenScopeService = apiTokenScopeService;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isApiTokenAuthentication(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        Set<String> tokenScopes = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("SCOPE_"))
            .map(authority -> authority.substring("SCOPE_".length()))
            .collect(Collectors.toSet());

        ApiTokenScopeService.AuthorizationDecision decision = apiTokenScopeService.authorize(
            request.getMethod(),
            request.getRequestURI(),
            tokenScopes
        );

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        accessDeniedHandler.handle(
            request,
            response,
            new AccessDeniedException(decision.message())
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || (!path.startsWith("/api/v1/")
                && !path.startsWith("/api/web/"));
    }

    private boolean isApiTokenAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        return principal instanceof PlatformPrincipal platformPrincipal
            && "api_token".equals(platformPrincipal.oauthProvider());
    }
}
