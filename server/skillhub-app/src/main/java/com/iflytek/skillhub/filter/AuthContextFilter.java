package com.iflytek.skillhub.filter;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthContextFilter extends OncePerRequestFilter {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;

    public AuthContextFilter(NamespaceMemberRepository namespaceMemberRepository,
                             UserAccountRepository userAccountRepository) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        PlatformPrincipal principal = resolvePrincipal(request);
        if (principal != null) {
            if (isInactiveUser(principal.userId())) {
                clearAuthentication(request);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            request.setAttribute("userId", principal.userId());
            Map<Long, NamespaceRole> userNsRoles = namespaceMemberRepository.findByUserId(principal.userId()).stream()
                    .collect(Collectors.toMap(
                            NamespaceMember::getNamespaceId,
                            NamespaceMember::getRole,
                            (left, right) -> left));
            request.setAttribute("userNsRoles", userNsRoles);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInactiveUser(String userId) {
        return userAccountRepository.findById(userId)
                .map(user -> !user.isActive())
                .orElse(true);
    }

    private void clearAuthentication(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute("platformPrincipal");
        session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        session.invalidate();
    }

    private PlatformPrincipal resolvePrincipal(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof PlatformPrincipal platformPrincipal) {
                return platformPrincipal;
            }
        }

        Object sessionPrincipal = request.getSession(false) != null
                ? request.getSession(false).getAttribute("platformPrincipal")
                : null;
        if (sessionPrincipal instanceof PlatformPrincipal platformPrincipal) {
            return platformPrincipal;
        }
        return null;
    }
}
