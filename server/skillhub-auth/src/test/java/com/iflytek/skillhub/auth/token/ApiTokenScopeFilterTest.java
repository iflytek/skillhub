package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ApiTokenScopeFilterTest {

    private final ApiTokenScopeService scopeService = new ApiTokenScopeService(new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDenyApiTokenWithoutRequiredScope() throws Exception {
        AccessDeniedHandler handler = (request, response, accessDeniedException) -> {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage());
        };
        ApiTokenScopeFilter filter = new ApiTokenScopeFilter(scopeService, handler);

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-1",
            "Alice",
            "alice@example.com",
            "",
            "api_token",
            Set.of("SKILL_ADMIN")
        );
        var authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(
                new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"),
                new SimpleGrantedAuthority("SCOPE_skill:read")
            )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/publish");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Missing API token scope: skill:publish"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowSessionAuthRequestsWithoutScopeChecks() throws Exception {
        AccessDeniedHandler handler = mock(AccessDeniedHandler.class);
        ApiTokenScopeFilter filter = new ApiTokenScopeFilter(scopeService, handler);

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-2",
            "Carol",
            "carol@example.com",
            "",
            "github",
            Set.of("SUPER_ADMIN")
        );
        var authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/users/user-2/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(handler, never()).handle(eq(request), eq(response), any());
    }

    @Test
    void shouldDenyApiWebRequestsWithoutRequiredScope() throws Exception {
        AccessDeniedHandler handler = (request, response, accessDeniedException) -> {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage());
        };
        ApiTokenScopeFilter filter = new ApiTokenScopeFilter(scopeService, handler);

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-3",
            "Bob",
            "bob@example.com",
            "",
            "api_token",
            Set.of("USER")
        );
        var authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/web/skills/global/publish");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Missing API token scope: skill:publish"));
        verify(chain, never()).doFilter(request, response);
    }
}
