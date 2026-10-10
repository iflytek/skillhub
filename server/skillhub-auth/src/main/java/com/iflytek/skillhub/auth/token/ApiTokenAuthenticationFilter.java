package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authenticates bearer tokens and projects them into a Spring Security
 * principal with both roles and token scopes.
 */
@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiTokenService apiTokenService;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;
    private final ApiTokenScopeService apiTokenScopeService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    public ApiTokenAuthenticationFilter(ApiTokenService apiTokenService,
                                        UserAccountRepository userRepo,
                                        UserRoleBindingRepository roleBindingRepo,
                                        ApiTokenScopeService apiTokenScopeService,
                                        AuthenticationEntryPoint authenticationEntryPoint) {
        this.apiTokenService = apiTokenService;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
        this.apiTokenScopeService = apiTokenScopeService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    ApiTokenAuthenticationFilter(ApiTokenService apiTokenService,
                                 UserAccountRepository userRepo,
                                 UserRoleBindingRepository roleBindingRepo,
                                 ApiTokenScopeService apiTokenScopeService) {
        this(apiTokenService, userRepo, roleBindingRepo, apiTokenScopeService,
            (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && isBearerAuthorization(authHeader)) {
            String rawToken = extractBearerToken(authHeader);
            if (rawToken == null) {
                rejectBearer(request, response);
                return;
            }

            var token = apiTokenService.validateToken(rawToken);
            if (token.isEmpty()) {
                rejectBearer(request, response);
                return;
            }

            ApiToken apiToken = token.get();
            var user = userRepo.findById(apiToken.getUserId());
            if (user.isEmpty() || !user.get().isActive()) {
                rejectBearer(request, response);
                return;
            }

            UserAccount userAccount = user.get();
            Set<String> roles = roleBindingRepo.findByUserId(userAccount.getId()).stream()
                .map(rb -> rb.getRole().getCode())
                .collect(Collectors.toSet());
            roles = PlatformRoleDefaults.withDefaultUserRole(roles);
            Set<String> scopes = apiTokenScopeService.parseScopes(apiToken.getScopeJson());
            PlatformPrincipal principal = new PlatformPrincipal(
                userAccount.getId(), userAccount.getDisplayName(), userAccount.getEmail(),
                userAccount.getAvatarUrl(), "api_token", roles
            );
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.addAll(roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList());
            authorities.addAll(scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList());
            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
            apiTokenService.touchLastUsed(apiToken);
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = RouteSecurityPolicyRegistry.requestPath(request);
        return !(path.startsWith("/api/v1/")
            || path.startsWith("/api/web/")
            || path.startsWith("/api/cli/"));
    }

    private boolean isBearerAuthorization(String authHeader) {
        if (!authHeader.regionMatches(true, 0, "Bearer", 0, "Bearer".length())) {
            return false;
        }
        return authHeader.length() == "Bearer".length()
            || Character.isWhitespace(authHeader.charAt("Bearer".length()));
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader.length() <= BEARER_PREFIX.length() - 1
            || authHeader.charAt(BEARER_PREFIX.length() - 1) != ' ') {
            return null;
        }
        String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        return rawToken.isEmpty() ? null : rawToken;
    }

    private void rejectBearer(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
            request,
            response,
            new BadCredentialsException("Invalid bearer token")
        );
    }
}
