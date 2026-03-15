package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
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
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseApiController {

    private final AuthMethodCatalog authMethodCatalog;
    private final SessionBootstrapService sessionBootstrapService;
    private final DirectAuthService directAuthService;
    private final AuthFailureThrottleService authFailureThrottleService;

    public AuthController(ApiResponseFactory responseFactory,
                          AuthMethodCatalog authMethodCatalog,
                          SessionBootstrapService sessionBootstrapService,
                          DirectAuthService directAuthService,
                          AuthFailureThrottleService authFailureThrottleService) {
        super(responseFactory);
        this.authMethodCatalog = authMethodCatalog;
        this.sessionBootstrapService = sessionBootstrapService;
        this.directAuthService = directAuthService;
        this.authFailureThrottleService = authFailureThrottleService;
    }

    @GetMapping("/me")
    public ApiResponse<AuthMeResponse> me(@AuthenticationPrincipal PlatformPrincipal principal,
                                          Authentication authentication) {
        if (principal == null || authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("error.auth.required");
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
