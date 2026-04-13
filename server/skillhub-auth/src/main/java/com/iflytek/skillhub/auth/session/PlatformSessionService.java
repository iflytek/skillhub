package com.iflytek.skillhub.auth.session;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Synchronizes {@link PlatformPrincipal} snapshots with Spring Security's
 * session-backed authentication context.
 */
@Service
public class PlatformSessionService {

    /**
     * Establishes a new authenticated session and rotates the session id to
     * reduce fixation risk.
     */
    public void establishSession(PlatformPrincipal principal, HttpServletRequest request) {
        establishSession(principal, request, true);
    }

    /**
     * Establishes a session for the supplied principal and optionally rotates
     * the underlying servlet session id.
     */
    public void establishSession(PlatformPrincipal principal,
                                 HttpServletRequest request,
                                 boolean rotateSessionId) {
        var authorities = principal.platformRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        persist(principal, authentication, request, rotateSessionId);
    }

    /**
     * Rebinds an updated principal to an already authenticated request without
     * discarding the existing authentication object.
     */
    public void attachToAuthenticatedSession(PlatformPrincipal principal,
                                             Authentication authentication,
                                             HttpServletRequest request) {
        attachToAuthenticatedSession(principal, authentication, request, false);
    }

    public void attachToAuthenticatedSession(PlatformPrincipal principal,
                                             Authentication authentication,
                                             HttpServletRequest request,
                                             boolean rotateSessionId) {
        // Create a new authentication with PlatformPrincipal as the principal
        // instead of using the OAuth2 authentication which has OAuth2User as principal
        var authorities = principal.platformRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        Authentication platformAuth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        persist(principal, platformAuth, request, rotateSessionId);
    }

    private void persist(PlatformPrincipal principal,
                         Authentication authentication,
                         HttpServletRequest request,
                         boolean rotateSessionId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        request.getSession(true);
        if (rotateSessionId) {
            request.changeSessionId();
        }
        request.getSession().setAttribute("platformPrincipal", principal);
        request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
