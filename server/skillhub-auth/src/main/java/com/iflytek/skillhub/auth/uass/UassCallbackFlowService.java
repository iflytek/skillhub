package com.iflytek.skillhub.auth.uass;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.auth.uass.store.UassLoginState;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Owns the callback-stage UASS browser login flow: consume login state,
 * validate the upstream result, resolve the local principal, and establish the
 * SkillHub session only after identity provisioning succeeds.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
@ConditionalOnBean(UassClientFacade.class)
public class UassCallbackFlowService {

    private final UassClientFacade uassClientFacade;
    private final UassLoginStateService uassLoginStateService;
    private final UassIdentityService uassIdentityService;
    private final PlatformSessionService platformSessionService;

    public UassCallbackFlowService(UassClientFacade uassClientFacade,
                                   UassLoginStateService uassLoginStateService,
                                   UassIdentityService uassIdentityService,
                                   PlatformSessionService platformSessionService) {
        this.uassClientFacade = uassClientFacade;
        this.uassLoginStateService = uassLoginStateService;
        this.uassIdentityService = uassIdentityService;
        this.platformSessionService = platformSessionService;
    }

    public String completeLogin(String loginCode,
                                String state,
                                URI callbackUri,
                                HttpServletRequest request) {
        UassLoginState loginState = uassLoginStateService.consumeForCallback(state)
                .orElseThrow(() -> new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.uass.stateInvalid"));
        UassLoginContext loginContext = uassClientFacade.validateLogin(loginCode, state, callbackUri);
        UassUserProfile userProfile = uassClientFacade.loadUserProfile(loginContext);
        try {
            platformSessionService.establishSession(
                    uassIdentityService.resolvePrincipal(loginContext, userProfile),
                    request
            );
        } catch (RuntimeException exception) {
            clearPartialSession(request);
            throw exception;
        }
        return loginState.returnTo();
    }

    private static void clearPartialSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
