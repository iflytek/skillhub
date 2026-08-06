package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
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
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class AuthContextFilter extends OncePerRequestFilter {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final PlatformSessionService platformSessionService;
    private final ApiResponseFactory apiResponseFactory;
    private final ObjectMapper objectMapper;
    private final boolean enforceActiveUserCheck;
    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    public AuthContextFilter(NamespaceMemberRepository namespaceMemberRepository,
                             UserAccountRepository userAccountRepository,
                             UserRoleBindingRepository userRoleBindingRepository,
                             PlatformSessionService platformSessionService,
                             ApiResponseFactory apiResponseFactory,
                             ObjectMapper objectMapper,
                             @Value("${skillhub.auth.enforce-active-user-check:true}") boolean enforceActiveUserCheck,
                             RouteSecurityPolicyRegistry routeSecurityPolicyRegistry) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.platformSessionService = platformSessionService;
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
        if (!routeSecurityPolicyRegistry.shouldProjectRequestContext(requestPath(request))) {
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
            principal = refreshSessionRolesIfNeeded(principal, request);
            request.setAttribute("userId", principal.userId());
            request.setAttribute("platformRoles", platformRoles(principal));
            Map<Long, NamespaceRole> userNsRoles = namespaceMemberRepository.findByUserId(principal.userId()).stream()
                    .collect(Collectors.toMap(
                            NamespaceMember::getNamespaceId,
                            NamespaceMember::getRole,
                            (left, right) -> left));
            request.setAttribute("userNsRoles", userNsRoles);
        }

        filterChain.doFilter(request, response);
    }

    private String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        return request.getRequestURI();
    }

    private boolean isInactiveUser(String userId) {
        if (!enforceActiveUserCheck) {
            return false;
        }
        return userAccountRepository.findById(userId)
                .map(user -> !user.isActive())
                .orElse(true);
    }

    private PlatformPrincipal refreshSessionRolesIfNeeded(PlatformPrincipal principal, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("platformPrincipal") instanceof PlatformPrincipal)) {
            return principal;
        }

        List<UserRoleBinding> roleBindings = userRoleBindingRepository.findByUserId(principal.userId());
        Set<String> freshRoles = PlatformRoleDefaults.withDefaultUserRole(
                (roleBindings != null ? roleBindings : List.<UserRoleBinding>of()).stream()
                        .map(binding -> binding.getRole().getCode())
                        .collect(Collectors.toSet()));
        if (freshRoles.equals(platformRoles(principal))) {
            return principal;
        }

        PlatformPrincipal refreshedPrincipal = new PlatformPrincipal(
                principal.userId(),
                principal.displayName(),
                principal.email(),
                principal.avatarUrl(),
                principal.oauthProvider(),
                freshRoles);
        platformSessionService.establishSession(refreshedPrincipal, request, false);
        return refreshedPrincipal;
    }

    private Set<String> platformRoles(PlatformPrincipal principal) {
        return principal.platformRoles() != null ? principal.platformRoles() : Set.of();
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
