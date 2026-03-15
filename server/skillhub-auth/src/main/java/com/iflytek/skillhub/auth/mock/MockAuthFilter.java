package com.iflytek.skillhub.auth.mock;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("local")
@Order(-100)
public class MockAuthFilter extends OncePerRequestFilter {

    private final UserAccountRepository userRepo;
    private final UserRoleBindingRepository roleBindingRepo;
    private final PlatformSessionService platformSessionService;

    public MockAuthFilter(UserAccountRepository userRepo,
                          UserRoleBindingRepository roleBindingRepo,
                          PlatformSessionService platformSessionService) {
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
        this.platformSessionService = platformSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String mockUserId = request.getHeader("X-Mock-User-Id");
        if (mockUserId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String userId = mockUserId;
            userRepo.findById(userId)
                .filter(UserAccount::isActive)
                .ifPresent(user -> {
                    Set<String> roles = roleBindingRepo.findByUserId(userId).stream()
                        .map(rb -> rb.getRole().getCode())
                        .collect(Collectors.toSet());
                    roles = PlatformRoleDefaults.withDefaultUserRole(roles);
                    var principal = new PlatformPrincipal(
                        user.getId(), user.getDisplayName(), user.getEmail(),
                        user.getAvatarUrl(), "mock", roles
                    );
                    platformSessionService.establishSession(principal, request, false);
                });
        }
        filterChain.doFilter(request, response);
    }
}
