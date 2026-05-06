package com.iflytek.skillhub.auth.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MockAuthFilterTest {

    private UserAccountRepository userRepo;
    private UserRoleBindingRepository roleBindingRepo;
    private PlatformSessionService platformSessionService;
    private MockAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserAccountRepository.class);
        roleBindingRepo = mock(UserRoleBindingRepository.class);
        platformSessionService = mock(PlatformSessionService.class);
        filter = new MockAuthFilter(userRepo, roleBindingRepo, platformSessionService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_noMockUserHeader_skips() throws Exception {
        when(request.getHeader("X-Mock-User-Id")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepo, platformSessionService);
    }

    @Test
    void doFilterInternal_existingAuthentication_skips() throws Exception {
        when(request.getHeader("X-Mock-User-Id")).thenReturn("user-123");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing", null));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepo, platformSessionService);
    }

    @Test
    void doFilterInternal_userNotFound_skips() throws Exception {
        when(request.getHeader("X-Mock-User-Id")).thenReturn("user-123");
        when(userRepo.findById("user-123")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(platformSessionService);
    }

    @Test
    void doFilterInternal_userInactive_skips() throws Exception {
        when(request.getHeader("X-Mock-User-Id")).thenReturn("user-123");
        UserAccount inactiveUser = new UserAccount("user-123", "Alice", "alice@example.com", null);
        inactiveUser.setStatus(com.iflytek.skillhub.domain.user.UserStatus.DISABLED);
        when(userRepo.findById("user-123")).thenReturn(Optional.of(inactiveUser));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(platformSessionService);
    }

    @Test
    void doFilterInternal_activeUser_establishesSession() throws Exception {
        when(request.getHeader("X-Mock-User-Id")).thenReturn("user-123");
        UserAccount user = new UserAccount("user-123", "Alice", "alice@example.com", "https://example.com/avatar.png");
        when(userRepo.findById("user-123")).thenReturn(Optional.of(user));

        Role role = mock(Role.class);
        when(role.getCode()).thenReturn("ADMIN");
        UserRoleBinding binding = new UserRoleBinding("user-123", role);
        when(roleBindingRepo.findByUserId("user-123")).thenReturn(List.of(binding));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(platformSessionService).establishSession(
                org.mockito.ArgumentMatchers.argThat(principal ->
                        principal.userId().equals("user-123")
                                && principal.displayName().equals("Alice")
                                && principal.email().equals("alice@example.com")
                                && principal.avatarUrl().equals("https://example.com/avatar.png")
                                && principal.oauthProvider().equals("mock")
                                && principal.platformRoles().contains("ADMIN")
                ),
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(false)
        );
    }

    @Test
    void doFilterInternal_activeUserWithNoRoles_addsDefaultUserRole() throws Exception {
        when(request.getHeader("X-Mock-User-Id")).thenReturn("user-123");
        UserAccount user = new UserAccount("user-123", "Alice", "alice@example.com", null);
        when(userRepo.findById("user-123")).thenReturn(Optional.of(user));
        when(roleBindingRepo.findByUserId("user-123")).thenReturn(List.of());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(platformSessionService).establishSession(
                org.mockito.ArgumentMatchers.argThat(principal ->
                        principal.platformRoles().equals(Set.of("USER"))
                ),
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(false)
        );
    }
}
