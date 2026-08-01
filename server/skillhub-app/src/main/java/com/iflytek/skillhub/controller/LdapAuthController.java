package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.LdapBindRequest;
import com.iflytek.skillhub.service.LdapBindingAppService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for binding an LDAP identity to the currently authenticated account.
 */
@RestController
@RequestMapping("/api/v1/auth/ldap")
public class LdapAuthController extends BaseApiController {

    private final LdapBindingAppService ldapBindingAppService;

    public LdapAuthController(ApiResponseFactory responseFactory,
                              LdapBindingAppService ldapBindingAppService) {
        super(responseFactory);
        this.ldapBindingAppService = ldapBindingAppService;
    }

    @PostMapping("/bind")
    public ApiResponse<Void> bind(@AuthenticationPrincipal PlatformPrincipal principal,
                                  @Valid @RequestBody LdapBindRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        ldapBindingAppService.bindLdapIdentity(principal.userId(), request.username(), request.password());
        return ok("response.success", null);
    }
}
