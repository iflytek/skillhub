package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiTokenService apiTokenService;
    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;

    public ApiTokenAuthenticationFilter(ApiTokenService apiTokenService,
                                         UserAccountRepository userRepo,
                                         UserRoleBindingRepository roleBindingRepo) {
        this.apiTokenService = apiTokenService;
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String rawToken = authHeader.substring(BEARER_PREFIX.length());
            apiTokenService.validateToken(rawToken).ifPresent(token -> {
                apiTokenService.touchLastUsed(token);
                userRepo.findById(token.getUserId()).ifPresent(user -> {
                    Set<String> roles = roleBindingRepo.findByUserId(user.getId()).stream()
                        .map(rb -> rb.getRole().getCode())
                        .collect(Collectors.toSet());
                    PlatformPrincipal principal = new PlatformPrincipal(
                        user.getId(), user.getDisplayName(), user.getEmail(),
                        user.getAvatarUrl(), "api_token", roles
                    );
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            });
        }
        filterChain.doFilter(request, response);
    }
}
