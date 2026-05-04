package com.iflytek.skillhub.auth.session;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSessionServiceTest {

    private final PlatformSessionService service = new PlatformSessionService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishSession_twoArg_rotatesSessionId() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Alice", "alice@example.com", "", "github", Set.of("USER")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        service.establishSession(principal, request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getSession().getAttribute("platformPrincipal")).isEqualTo(principal);
        assertThat(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNotNull();
    }

    @Test
    void establishSession_threeArg_withRotation() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Alice", "alice@example.com", "", "github", Set.of("USER")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        service.establishSession(principal, request, true);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getSession().getAttribute("platformPrincipal")).isEqualTo(principal);
    }

    @Test
    void establishSession_threeArg_withoutRotation() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Alice", "alice@example.com", "", "github", Set.of("USER")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        service.establishSession(principal, request, false);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getSession().getAttribute("platformPrincipal")).isEqualTo(principal);
    }

    @Test
    void attachToAuthenticatedSession_threeArg_noRotation() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Alice", "alice@example.com", "", "github", Set.of("USER")
        );
        Authentication existingAuth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        service.attachToAuthenticatedSession(principal, existingAuth, request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getSession().getAttribute("platformPrincipal")).isEqualTo(principal);
    }

    @Test
    void attachToAuthenticatedSession_fourArg_withRotation() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Alice", "alice@example.com", "", "github", Set.of("USER")
        );
        Authentication existingAuth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        service.attachToAuthenticatedSession(principal, existingAuth, request, true);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getSession().getAttribute("platformPrincipal")).isEqualTo(principal);
    }

    @Test
    void attachToAuthenticatedSession_fourArg_withoutRotation() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "Alice", "alice@example.com", "", "github", Set.of("USER")
        );
        Authentication existingAuth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        service.attachToAuthenticatedSession(principal, existingAuth, request, false);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getSession().getAttribute("platformPrincipal")).isEqualTo(principal);
    }
}
