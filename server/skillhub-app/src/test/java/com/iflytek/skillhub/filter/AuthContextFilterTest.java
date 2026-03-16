package com.iflytek.skillhub.filter;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthContextFilterTest {

    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final AuthContextFilter filter = new AuthContextFilter(namespaceMemberRepository, userAccountRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledSessionUser_shouldInvalidateSessionAndBlockRequest() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("user-1", "Alice", "alice@example.com", null, "local", Set.of("USER"));
        UserAccount user = new UserAccount("Alice", "alice@example.com");
        user.setStatus(UserStatus.DISABLED);

        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute("platformPrincipal", principal);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(userAccountRepository.findById("user-1")).thenReturn(java.util.Optional.of(user));

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(!request.isRequestedSessionIdValid() || request.getSession(false) == null);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void activeSessionUser_shouldPopulateRequestContextAndContinue() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("user-2", "Bob", "bob@example.com", null, "local", Set.of("USER"));
        UserAccount user = new UserAccount("Bob", "bob@example.com");
        user.setStatus(UserStatus.ACTIVE);
        NamespaceMember member = new NamespaceMember(9L, "user-2", NamespaceRole.ADMIN);

        MockHttpServletRequest request = new MockHttpServletRequest();
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
}
