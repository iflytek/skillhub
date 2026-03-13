package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuthMeResponse;
import com.iflytek.skillhub.dto.ChangePasswordRequest;
import com.iflytek.skillhub.dto.LocalLoginRequest;
import com.iflytek.skillhub.dto.LocalRegisterRequest;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/local")
public class LocalAuthController extends BaseApiController {

    private final LocalAuthService localAuthService;
    private final SkillHubMetrics skillHubMetrics;

    public LocalAuthController(ApiResponseFactory responseFactory,
                               LocalAuthService localAuthService,
                               SkillHubMetrics skillHubMetrics) {
        super(responseFactory);
        this.localAuthService = localAuthService;
        this.skillHubMetrics = skillHubMetrics;
    }

    @PostMapping("/register")
    public ApiResponse<AuthMeResponse> register(@Valid @RequestBody LocalRegisterRequest request,
                                                HttpServletRequest httpRequest) {
        PlatformPrincipal principal = localAuthService.register(request.username(), request.password(), request.email());
        skillHubMetrics.incrementUserRegister();
        establishSession(principal, httpRequest);
        return ok("response.success.created", AuthMeResponse.from(principal));
    }

    @PostMapping("/login")
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
        establishSession(principal, httpRequest);
        return ok("response.success.read", AuthMeResponse.from(principal));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal PlatformPrincipal principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        localAuthService.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        return ok("response.success.updated", null);
    }

    private void establishSession(PlatformPrincipal principal, HttpServletRequest request) {
        var authorities = principal.platformRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute("platformPrincipal", principal);
        request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
