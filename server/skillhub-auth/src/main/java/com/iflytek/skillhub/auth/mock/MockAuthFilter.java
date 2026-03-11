package com.iflytek.skillhub.auth.mock;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    public MockAuthFilter(UserAccountRepository userRepo,
                          UserRoleBindingRepository roleBindingRepo) {
        this.userRepo = userRepo;
        this.roleBindingRepo = roleBindingRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String mockUserId = request.getHeader("X-Mock-User-Id");
        if (mockUserId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = Long.parseLong(mockUserId);
            userRepo.findById(userId)
                .filter(UserAccount::isActive)
                .ifPresent(user -> {
                    Set<String> roles = roleBindingRepo.findByUserId(userId).stream()
                        .map(rb -> rb.getRole().getCode())
                        .collect(Collectors.toSet());
                    var principal = new PlatformPrincipal(
                        user.getId(), user.getDisplayName(), user.getEmail(),
                        user.getAvatarUrl(), "mock", roles
                    );
                    var authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.getSession().setAttribute("platformPrincipal", principal);
                });
        }
        filterChain.doFilter(request, response);
    }
}
