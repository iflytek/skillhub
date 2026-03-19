package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Projects the authenticated principal into request attributes consumed by the controller layer.
 */
@Component
public class AuthContextFilter extends OncePerRequestFilter {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;
    private final ApiResponseFactory apiResponseFactory;
    private final ObjectMapper objectMapper;
    private final boolean enforceActiveUserCheck;
    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    public AuthContextFilter(NamespaceMemberRepository namespaceMemberRepository,
                             UserAccountRepository userAccountRepository,
                             ApiResponseFactory apiResponseFactory,
                             ObjectMapper objectMapper,
                             @Value("${skillhub.auth.enforce-active-user-check:true}") boolean enforceActiveUserCheck,
                             RouteSecurityPolicyRegistry routeSecurityPolicyRegistry) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userAccountRepository = userAccountRepository;
        this.apiResponseFactory = apiResponseFactory;
        this.objectMapper = objectMapper;
        this.enforceActiveUserCheck = enforceActiveUserCheck;
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!routeSecurityPolicyRegistry.shouldProjectRequestContext(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        PlatformPrincipal principal = resolvePrincipal(request);
        if (principal != null) {
            if (isInactiveUser(principal.userId())) {
                clearAuthentication(request);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(
                        response.getOutputStream(),
                        apiResponseFactory.error(HttpServletResponse.SC_UNAUTHORIZED, "error.auth.local.accountDisabled")
                );
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
        if (!enforceActiveUserCheck) {
            return false;
        }
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
