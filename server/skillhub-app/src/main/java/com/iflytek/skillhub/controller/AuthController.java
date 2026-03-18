package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuthMeResponse;
import com.iflytek.skillhub.dto.AuthMethodResponse;
import com.iflytek.skillhub.dto.AuthProviderResponse;
import com.iflytek.skillhub.dto.DirectLoginRequest;
import com.iflytek.skillhub.dto.SessionBootstrapRequest;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.service.AuthMethodCatalog;
import com.iflytek.skillhub.service.DirectAuthService;
import com.iflytek.skillhub.service.SessionBootstrapService;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseApiController {

    private final AuthMethodCatalog authMethodCatalog;
    private final SessionBootstrapService sessionBootstrapService;
    private final DirectAuthService directAuthService;
    private final AuthFailureThrottleService authFailureThrottleService;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final PlatformSessionService platformSessionService;
    private final UserAccountRepository userAccountRepository;

    public AuthController(ApiResponseFactory responseFactory,
                          AuthMethodCatalog authMethodCatalog,
                          SessionBootstrapService sessionBootstrapService,
                          DirectAuthService directAuthService,
                          AuthFailureThrottleService authFailureThrottleService,
                          UserRoleBindingRepository userRoleBindingRepository,
                          PlatformSessionService platformSessionService,
                          UserAccountRepository userAccountRepository) {
        super(responseFactory);
        this.authMethodCatalog = authMethodCatalog;
        this.sessionBootstrapService = sessionBootstrapService;
        this.directAuthService = directAuthService;
        this.authFailureThrottleService = authFailureThrottleService;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.platformSessionService = platformSessionService;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/me")
    public ApiResponse<AuthMeResponse> me(@AuthenticationPrincipal PlatformPrincipal principal,
                                          Authentication authentication,
                                          HttpServletRequest request) {
        if (principal == null || authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("error.auth.required");
        }
        UserAccount user = userAccountRepository.findById(principal.userId()).orElse(null);
        if (user == null || user.getStatus() == UserStatus.DISABLED) {
            request.getSession().invalidate();
            throw new UnauthorizedException("error.auth.required");
        }
        Set<String> freshRoles = PlatformRoleDefaults.withDefaultUserRole(
                userRoleBindingRepository.findByUserId(principal.userId()).stream()
                        .map(binding -> binding.getRole().getCode())
                        .collect(Collectors.toSet()));

        // Detect stale session: check if roles or profile fields have changed
        boolean rolesChanged = !freshRoles.equals(principal.platformRoles());
        boolean displayNameChanged = !user.getDisplayName().equals(principal.displayName());
        boolean avatarChanged = !java.util.Objects.equals(user.getAvatarUrl(), principal.avatarUrl());

        if (rolesChanged || displayNameChanged || avatarChanged) {
            principal = new PlatformPrincipal(
                    principal.userId(),
                    user.getDisplayName(),    // use DB value (may have been updated via profile)
                    principal.email(),
                    user.getAvatarUrl(),      // use DB value
                    principal.oauthProvider(),
                    freshRoles);
            platformSessionService.establishSession(principal, request, false);
        }
        return ok("response.success.read", AuthMeResponse.from(principal));
    }

    @GetMapping("/providers")
    public ApiResponse<List<AuthProviderResponse>> providers(
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        return ok("response.success.read", authMethodCatalog.listOAuthProviders(returnTo));
    }

    @GetMapping("/methods")
    public ApiResponse<List<AuthMethodResponse>> methods(
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        return ok("response.success.read", authMethodCatalog.listMethods(returnTo));
    }

    @PostMapping("/session/bootstrap")
    @RateLimit(category = "auth-session-bootstrap", authenticated = 30, anonymous = 15, windowSeconds = 60)
    public ApiResponse<AuthMeResponse> bootstrapSession(@Valid @RequestBody SessionBootstrapRequest request,
                                                        HttpServletRequest httpRequest) {
        return ok(
            "response.success.read",
            AuthMeResponse.from(sessionBootstrapService.bootstrap(request.provider(), httpRequest))
        );
    }

    @PostMapping("/direct/login")
    @RateLimit(category = "auth-direct-login", authenticated = 20, anonymous = 10, windowSeconds = 60)
    public ApiResponse<AuthMeResponse> directLogin(@Valid @RequestBody DirectLoginRequest request,
                                                   HttpServletRequest httpRequest) {
        String category = "direct:" + request.provider();
        String clientIp = resolveClientIp(httpRequest);
        authFailureThrottleService.assertAllowed(category, request.username(), clientIp);
        PlatformPrincipal principal;
        try {
            principal = directAuthService.authenticate(
                    request.provider(),
                    request.username(),
                    request.password(),
                    httpRequest
            );
        } catch (AuthFlowException ex) {
            if (HttpStatus.UNAUTHORIZED.equals(ex.getStatus())) {
                authFailureThrottleService.recordFailure(category, request.username(), clientIp);
            }
            throw ex;
        }
        authFailureThrottleService.resetIdentifier(category, request.username());
        return ok(
            "response.success.read",
            AuthMeResponse.from(principal)
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

}
