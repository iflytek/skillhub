package com.iflytek.skillhub.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiTokenAuthenticationFilterTest {

    private final ApiTokenService apiTokenService = mock(ApiTokenService.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserRoleBindingRepository roleBindingRepository = mock(UserRoleBindingRepository.class);
    private final ApiTokenScopeService scopeService =
            new ApiTokenScopeService(new ObjectMapper(), new RouteSecurityPolicyRegistry());
    private final ApiTokenAuthenticationFilter filter = new ApiTokenAuthenticationFilter(
        apiTokenService,
        userAccountRepository,
        roleBindingRepository,
        scopeService
    );

    @BeforeEach
    void initializeSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPopulateRoleAndScopeAuthoritiesForActiveUser() throws Exception {
        ApiToken token = new ApiToken("user-1", "cli", "sk_test", "hash", "[\"skill:publish\",\"token:manage\"]");
        UserAccount user = new UserAccount("user-1", "Alice", "alice@example.com", "");
        UserRoleBinding binding = mock(UserRoleBinding.class);
        Role role = mock(Role.class);

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(roleBindingRepository.findByUserId("user-1")).thenReturn(List.of(binding));
        when(binding.getRole()).thenReturn(role);
        when(role.getCode()).thenReturn("SKILL_ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/whoami");
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_SKILL_ADMIN")));
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("SCOPE_skill:publish")));
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("SCOPE_token:manage")));
        verify(apiTokenService).touchLastUsed(token);
    }

    @Test
    void shouldRejectDisabledUsers() throws Exception {
        ApiToken token = new ApiToken("user-2", "cli", "sk_test", "hash", "[\"skill:publish\"]");
        UserAccount user = new UserAccount("user-2", "Bob", "bob@example.com", "");
        user.setStatus(UserStatus.DISABLED);

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-2")).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/publish");
        request.addHeader("Authorization", "Bearer raw-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(MockHttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertNull(chain.getRequest());
        verify(apiTokenService, never()).touchLastUsed(token);
    }

    @Test
    void shouldRejectUnknownBearerTokenOnCliReadRoutes() throws Exception {
        when(apiTokenService.validateToken("unknown-token")).thenReturn(Optional.empty());

        for (String route : cliReadRoutes()) {
            SecurityContextHolder.clearContext();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", route);
            request.addHeader("Authorization", "Bearer unknown-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(MockHttpServletResponse.SC_UNAUTHORIZED, response.getStatus(), route);
            assertNull(SecurityContextHolder.getContext().getAuthentication(), route);
            assertNull(chain.getRequest(), route);
        }
    }

    @Test
    void shouldRejectBearerTokenWhenUserIsMissing() throws Exception {
        ApiToken token = new ApiToken("missing-user", "cli", "sk_test", "hash", "[]");

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("missing-user")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cli/v1/skills/search");
        request.addHeader("Authorization", "Bearer raw-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(MockHttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        verify(apiTokenService, never()).touchLastUsed(token);
    }

    @Test
    void shouldRejectEmptyBearerTokenWithoutValidatingIt() throws Exception {
        when(apiTokenService.validateToken("")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cli/v1/skills/search");
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(MockHttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        verify(apiTokenService, never()).validateToken(any());
    }

    @Test
    void shouldRejectMalformedBearerHeaderWithoutValidatingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cli/v1/skills/search");
        request.addHeader("Authorization", "Bearer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(MockHttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
        verify(apiTokenService, never()).validateToken(any());
    }

    @Test
    void shouldAllowAnonymousCliReadsWhenAuthorizationHeaderIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cli/v1/skills/search");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(MockHttpServletResponse.SC_OK, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        verify(apiTokenService, never()).validateToken(any());
    }

    @Test
    void shouldIgnoreNonBearerAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cli/v1/skills/search");
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(MockHttpServletResponse.SC_OK, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        verify(apiTokenService, never()).validateToken(any());
    }

    @Test
    void shouldAuthenticateBearerTokensForApiWebRequests() throws Exception {
        ApiToken token = new ApiToken("user-3", "cli", "sk_test", "hash", "[\"skill:publish\"]");
        UserAccount user = new UserAccount("user-3", "Carol", "carol@example.com", "");

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-3")).thenReturn(Optional.of(user));
        when(roleBindingRepository.findByUserId("user-3")).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/web/skills/global/publish");
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(apiTokenService).touchLastUsed(token);
    }

    @Test
    void shouldAuthenticateBearerTokensBehindForwardedPrefix() throws Exception {
        ApiToken token = new ApiToken("user-4", "cli", "sk_test", "hash", "[\"skill:read\"]");
        UserAccount user = new UserAccount("user-4", "Dana", "dana@example.com", "");

        when(apiTokenService.validateToken("raw-token")).thenReturn(Optional.of(token));
        when(userAccountRepository.findById("user-4")).thenReturn(Optional.of(user));
        when(roleBindingRepository.findByUserId("user-4")).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/skillhub/api/cli/v1/auth/whoami");
        request.setContextPath("/skillhub");
        request.setServletPath("/api/cli/v1/auth/whoami");
        request.addHeader("Authorization", "Bearer raw-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(apiTokenService).touchLastUsed(token);
    }

    private static List<String> cliReadRoutes() {
        return Stream.of(
                "/api/cli/v1/skills/search",
                "/api/cli/v1/skills/global/demo/resolve",
                "/api/cli/v1/skills/global/demo/download",
                "/api/cli/v1/skills/global/demo/versions/1.0.0/download"
        ).toList();
    }
}
