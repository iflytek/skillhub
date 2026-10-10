package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.bootstrap.PassiveSessionAuthenticator;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.config.AuthSessionBootstrapProperties;
import com.iflytek.skillhub.exception.BadRequestException;
import com.iflytek.skillhub.exception.ForbiddenException;
import com.iflytek.skillhub.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Restores a platform session from a passive authenticator and persists the
 * resulting principal into Spring Security's session context.
 */
@Service
public class SessionBootstrapService {

    private final AuthSessionBootstrapProperties properties;
    private final Map<String, PassiveSessionAuthenticator> authenticatorsByProvider;
    private final PlatformSessionService platformSessionService;

    public SessionBootstrapService(AuthSessionBootstrapProperties properties,
                                   List<PassiveSessionAuthenticator> authenticators,
                                   PlatformSessionService platformSessionService) {
        this.properties = properties;
        this.authenticatorsByProvider = authenticators.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                PassiveSessionAuthenticator::providerCode,
                Function.identity()
            ));
        this.platformSessionService = platformSessionService;
    }

    public PlatformPrincipal bootstrap(String providerCode, HttpServletRequest request) {
        if (!properties.isEnabled()) {
            throw new ForbiddenException("error.auth.sessionBootstrap.disabled");
        }

        PassiveSessionAuthenticator authenticator = authenticatorsByProvider.get(providerCode);
        if (authenticator == null) {
            throw new BadRequestException("error.auth.sessionBootstrap.providerUnsupported", providerCode);
        }

        PlatformPrincipal principal = authenticator.authenticate(request)
            .orElseThrow(() -> new UnauthorizedException("error.auth.sessionBootstrap.notAuthenticated"));
        platformSessionService.establishSession(principal, request);
        return principal;
    }

    public void establishSession(PlatformPrincipal principal, HttpServletRequest request) {
        platformSessionService.establishSession(principal, request);
    }
}
