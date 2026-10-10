package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.support.StaticMessageSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthContextFilterTest {

    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final AuthContextFilter filter;

    AuthContextFilterTest() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.auth.local.accountDisabled", Locale.ENGLISH, "This account has been disabled");
        Clock clock = Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
        ApiResponseFactory apiResponseFactory =
                new ApiResponseFactory(messageSource, clock, new RequestIdAccessor());
        filter = new AuthContextFilter(
                namespaceMemberRepository,
                userAccountRepository,
                apiResponseFactory,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                true,
                new RouteSecurityPolicyRegistry()
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledSessionUser_shouldClearAuthenticationWithoutInvalidatingSession() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("user-1", "Alice", "alice@example.com", null, "local", Set.of("USER"));
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/me");
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute("platformPrincipal", principal);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(userAccountRepository.findById("user-1")).thenReturn(java.util.Optional.of(user));

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
        assertFalse(session.isInvalid());
        assertNull(session.getAttribute("platformPrincipal"));
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void disabledSessionUser_shouldContinuePublicGetAsAnonymous() throws Exception {
        PlatformPrincipal principal = principal("user-public");
        UserAccount user = disabledUser("user-public");
        MockHttpServletRequest request = authenticatedRequest("GET", "/api/v1/skills", principal);
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(userAccountRepository.findById("user-public")).thenReturn(java.util.Optional.of(user));

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertFalse(session.isInvalid());
        assertNull(session.getAttribute("platformPrincipal"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void disabledSessionUser_shouldBlockProtectedMethodOnOtherwisePublicPath() throws Exception {
        PlatformPrincipal principal = principal("user-protected");
        MockHttpServletRequest request = authenticatedRequest("POST", "/api/v1/skills", principal);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(userAccountRepository.findById("user-protected"))
                .thenReturn(java.util.Optional.of(disabledUser("user-protected")));

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void missingSessionUser_shouldBeClearedOnceAndNotResurrectedOnNextPublicRequest() throws Exception {
        PlatformPrincipal principal = principal("deleted-user");
        MockHttpServletRequest firstRequest = authenticatedRequest("GET", "/api/v1/search", principal);
        MockHttpSession session = (MockHttpSession) firstRequest.getSession(false);
        FilterChain firstChain = mock(FilterChain.class);
        when(userAccountRepository.findById("deleted-user")).thenReturn(java.util.Optional.empty());

        filter.doFilter(firstRequest, new MockHttpServletResponse(), firstChain);
        SecurityContextHolder.clearContext();

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setMethod("GET");
        secondRequest.setRequestURI("/api/v1/search");
        secondRequest.setSession(session);
        FilterChain secondChain = mock(FilterChain.class);
        filter.doFilter(secondRequest, new MockHttpServletResponse(), secondChain);

        assertFalse(session.isInvalid());
        assertNull(session.getAttribute("platformPrincipal"));
        verify(firstChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(secondChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(userAccountRepository, times(1)).findById("deleted-user");
    }

    @Test
    void activeSessionUser_shouldPopulateRequestContextAndContinue() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("user-2", "Bob", "bob@example.com", null, "local", Set.of("USER"));
        UserAccount user = new UserAccount("user-2", "Bob", "bob@example.com", null);
        user.setStatus(UserStatus.ACTIVE);
        NamespaceMember member = new NamespaceMember(9L, "user-2", NamespaceRole.ADMIN);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/me");
        request.getSession(true).setAttribute("platformPrincipal", principal);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(userAccountRepository.findById("user-2")).thenReturn(java.util.Optional.of(user));
        when(namespaceMemberRepository.findByUserId("user-2")).thenReturn(List.of(member));

        filter.doFilter(request, response, filterChain);

        assertEquals("user-2", request.getAttribute("userId"));
        assertEquals(NamespaceRole.ADMIN, ((java.util.Map<Long, NamespaceRole>) request.getAttribute("userNsRoles")).get(9L));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void forwardedPrefixApiRequest_shouldUseServletPathForContextProjection() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("user-3", "Cara", "cara@example.com", null, "local", Set.of("USER"));
        UserAccount user = new UserAccount("user-3", "Cara", "cara@example.com", null);
        user.setStatus(UserStatus.ACTIVE);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/skillhub");
        request.setRequestURI("/skillhub/api/web/me/namespaces");
        request.setServletPath("/api/web/me/namespaces");
        request.getSession(true).setAttribute("platformPrincipal", principal);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(userAccountRepository.findById("user-3")).thenReturn(java.util.Optional.of(user));
        when(namespaceMemberRepository.findByUserId("user-3")).thenReturn(List.of());

        filter.doFilter(request, response, filterChain);

        assertEquals("user-3", request.getAttribute("userId"));
        assertTrue(((java.util.Map<Long, NamespaceRole>) request.getAttribute("userNsRoles")).isEmpty());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anonymousRequest_shouldPassThroughWithoutLoadingUserContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/assets/app.js");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertNull(request.getAttribute("userId"));
        assertNull(request.getAttribute("userNsRoles"));
        verify(filterChain).doFilter(same(request), same(response));
        verify(userAccountRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
        verify(namespaceMemberRepository, never()).findByUserId(org.mockito.ArgumentMatchers.anyString());
    }

    private static PlatformPrincipal principal(String userId) {
        return new PlatformPrincipal(userId, "Test User", userId + "@example.com", null, "local", Set.of("USER"));
    }

    private static UserAccount disabledUser(String userId) {
        UserAccount user = new UserAccount(userId, "Test User", userId + "@example.com", null);
        user.setStatus(UserStatus.DISABLED);
        return user;
    }

    private static MockHttpServletRequest authenticatedRequest(
            String method, String path, PlatformPrincipal principal) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.getSession(true).setAttribute("platformPrincipal", principal);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
        return request;
    }
}
