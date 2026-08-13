package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsResponse;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsUpdateRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.PersonalNamespaceSettingsAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide settings an operator can change without redeploying.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSystemSettingController extends BaseApiController {

    private final PersonalNamespaceSettingsAppService personalNamespaceSettingsAppService;

    public AdminSystemSettingController(PersonalNamespaceSettingsAppService personalNamespaceSettingsAppService,
                                        ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.personalNamespaceSettingsAppService = personalNamespaceSettingsAppService;
    }

    @GetMapping("/personal-namespace")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PersonalNamespaceSettingsResponse> getPersonalNamespaceSettings() {
        return ok("response.success.read", personalNamespaceSettingsAppService.get());
    }

    @PutMapping("/personal-namespace")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PersonalNamespaceSettingsResponse> updatePersonalNamespaceSettings(
            @Valid @RequestBody PersonalNamespaceSettingsUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", personalNamespaceSettingsAppService.update(
                request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }
}
