package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthContextFilterTest {

    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final AuthContextFilter filter;

    AuthContextFilterTest() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.auth.local.accountDisabled", Locale.ENGLISH, "This account has been disabled");
        ApiResponseFactory apiResponseFactory = new ApiResponseFactory(messageSource);
        filter = new AuthContextFilter(
                namespaceMemberRepository,
                userAccountRepository,
                apiResponseFactory,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                true
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledSessionUser_shouldInvalidateSessionAndBlockRequest() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("user-1", "Alice", "alice@example.com", null, "local", Set.of("USER"));
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        MockHttpServletRequest request = new MockHttpServletRequest();
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
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void activeSessionUser_shouldPopulateRequestContextAndContinue() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("user-2", "Bob", "bob@example.com", null, "local", Set.of("USER"));
        UserAccount user = new UserAccount("user-2", "Bob", "bob@example.com", null);
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
