package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuthMeResponse;
import com.iflytek.skillhub.dto.ChangePasswordRequest;
import com.iflytek.skillhub.dto.LocalLoginRequest;
import com.iflytek.skillhub.dto.LocalRegisterRequest;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/local")
public class LocalAuthController extends BaseApiController {

    private final LocalAuthService localAuthService;
    private final SkillHubMetrics skillHubMetrics;
    private final PlatformSessionService platformSessionService;

    public LocalAuthController(ApiResponseFactory responseFactory,
                               LocalAuthService localAuthService,
                               SkillHubMetrics skillHubMetrics,
                               PlatformSessionService platformSessionService) {
        super(responseFactory);
        this.localAuthService = localAuthService;
        this.skillHubMetrics = skillHubMetrics;
        this.platformSessionService = platformSessionService;
    }

    @PostMapping("/register")
    @RateLimit(category = "auth-register", authenticated = 10, anonymous = 5, windowSeconds = 300)
    public ApiResponse<AuthMeResponse> register(@Valid @RequestBody LocalRegisterRequest request,
                                                HttpServletRequest httpRequest) {
        PlatformPrincipal principal = localAuthService.register(request.username(), request.password(), request.email());
        skillHubMetrics.incrementUserRegister();
        platformSessionService.establishSession(principal, httpRequest);
        return ok("response.success.created", AuthMeResponse.from(principal));
    }

    @PostMapping("/login")
    @RateLimit(category = "auth-local-login", authenticated = 20, anonymous = 10, windowSeconds = 60)
    public ApiResponse<AuthMeResponse> login(@Valid @RequestBody LocalLoginRequest request,
                                             HttpServletRequest httpRequest) {
        PlatformPrincipal principal;
        try {
            principal = localAuthService.login(request.username(), request.password());
        } catch (RuntimeException ex) {
            skillHubMetrics.recordLocalLogin(false);
            throw ex;
        }
        skillHubMetrics.recordLocalLogin(true);
        platformSessionService.establishSession(principal, httpRequest);
        return ok("response.success.read", AuthMeResponse.from(principal));
    }

    @PostMapping("/change-password")
    @RateLimit(category = "auth-change-password", authenticated = 5, anonymous = 20, windowSeconds = 300)
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal PlatformPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        localAuthService.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        return ok("response.success.updated", null);
    }
}
