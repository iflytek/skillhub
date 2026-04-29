package com.iflytek.skillhub.auth.uass;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Handles UASS-specific browser status checks and logout cleanup while keeping
 * the local SkillHub session as the primary source of truth.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
@ConditionalOnBean(UassClientFacade.class)
public class UassSessionFlowService {

    private static final Logger log = LoggerFactory.getLogger(UassSessionFlowService.class);

    private final UassClientFacade uassClientFacade;
    private final UassLoginStateService uassLoginStateService;
    private final UassSessionContextService uassSessionContextService;

    public UassSessionFlowService(UassClientFacade uassClientFacade,
                                  UassLoginStateService uassLoginStateService,
                                  UassSessionContextService uassSessionContextService) {
        this.uassClientFacade = uassClientFacade;
        this.uassLoginStateService = uassLoginStateService;
        this.uassSessionContextService = uassSessionContextService;
    }

    public UassSessionStatus status(Authentication authentication, HttpServletRequest request) {
        PlatformPrincipal principal = resolvePrincipal(authentication);
        boolean authenticated = principal != null;
        String provider = principal == null ? null : principal.oauthProvider();
        Boolean remoteAuthenticated = null;
        if (authenticated && UassIdentityService.PROVIDER_CODE.equals(provider)) {
            remoteAuthenticated = remoteStatus(request);
        }
        return new UassSessionStatus(authenticated, provider, remoteAuthenticated);
    }

    public void logout(HttpServletRequest request) {
        Optional<UassLoginContext> loginContext = uassSessionContextService.load(request);
        try {
            loginContext.ifPresent(this::logoutRemoteSafely);
        } finally {
            loginContext.map(UassLoginContext::state).ifPresent(uassLoginStateService::clearFailedCallback);
            uassSessionContextService.clear(request);
            invalidateSession(request);
            SecurityContextHolder.clearContext();
        }
    }

    private Boolean remoteStatus(HttpServletRequest request) {
        return uassSessionContextService.load(request)
                .map(this::checkRemoteStatusSafely)
                .orElse(null);
    }

    private Boolean checkRemoteStatusSafely(UassLoginContext loginContext) {
        try {
            return uassClientFacade.checkLoginStatus(loginContext);
        } catch (RuntimeException exception) {
            log.warn("UASS remote status check failed; keeping local session as source of truth", exception);
            return null;
        }
    }

    private void logoutRemoteSafely(UassLoginContext loginContext) {
        try {
            uassClientFacade.logout(loginContext);
        } catch (RuntimeException exception) {
            log.warn("UASS remote logout failed; local session will still be cleared", exception);
        }
    }

    private static void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private static PlatformPrincipal resolvePrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof PlatformPrincipal platformPrincipal ? platformPrincipal : null;
    }

    public record UassSessionStatus(boolean authenticated, String provider, Boolean remoteAuthenticated) {
    }
}
