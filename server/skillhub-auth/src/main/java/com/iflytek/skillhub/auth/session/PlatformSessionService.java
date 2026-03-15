package com.iflytek.skillhub.auth.session;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class PlatformSessionService {

    public void establishSession(PlatformPrincipal principal, HttpServletRequest request) {
        establishSession(principal, request, true);
    }

    public void establishSession(PlatformPrincipal principal,
                                 HttpServletRequest request,
                                 boolean rotateSessionId) {
        var authorities = principal.platformRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        persist(principal, authentication, request, rotateSessionId);
    }

    public void attachToAuthenticatedSession(PlatformPrincipal principal,
                                             Authentication authentication,
                                             HttpServletRequest request) {
        attachToAuthenticatedSession(principal, authentication, request, false);
    }

    public void attachToAuthenticatedSession(PlatformPrincipal principal,
                                             Authentication authentication,
                                             HttpServletRequest request,
                                             boolean rotateSessionId) {
        persist(principal, authentication, request, rotateSessionId);
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
