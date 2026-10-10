package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.ExternalRoleGrantCreateRequest;
import com.iflytek.skillhub.dto.ExternalRoleGrantRuleResponse;
import com.iflytek.skillhub.dto.ExternalRoleGrantUpdateRequest;
import com.iflytek.skillhub.dto.PlatformRoleResponse;
import com.iflytek.skillhub.dto.SystemAuthSettingsResponse;
import com.iflytek.skillhub.dto.SystemAuthSettingsUpdateRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SystemAuthSettingsAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system-config")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SystemAuthSettingsController extends BaseApiController {
    private final SystemAuthSettingsAppService service;

    public SystemAuthSettingsController(SystemAuthSettingsAppService service, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.service = service;
    }

    @GetMapping("/auth/local")
    public ApiResponse<SystemAuthSettingsResponse> getLocalSettings() {
        return ok("response.success.read", service.getLocalSettings());
    }

    @PutMapping("/auth/local")
    public ApiResponse<SystemAuthSettingsResponse> updateLocalSettings(
            @Valid @RequestBody SystemAuthSettingsUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", service.updateLocalSettings(
                request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @GetMapping("/roles")
    public ApiResponse<List<PlatformRoleResponse>> listRoles() {
        return ok("response.success.read", service.listRoles());
    }

    @GetMapping("/role-grants")
    public ApiResponse<List<ExternalRoleGrantRuleResponse>> listRoleGrants() {
        return ok("response.success.read", service.listRules());
    }

    @PostMapping("/role-grants")
    public ApiResponse<ExternalRoleGrantRuleResponse> createRoleGrant(
            @Valid @RequestBody ExternalRoleGrantCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", service.createRule(
                request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @PutMapping("/role-grants/{id}")
    public ApiResponse<ExternalRoleGrantRuleResponse> updateRoleGrant(
            @PathVariable long id,
            @Valid @RequestBody ExternalRoleGrantUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", service.updateRule(
                id, request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/role-grants/{id}")
    public ApiResponse<ExternalRoleGrantRuleResponse> disableRoleGrant(
            @PathVariable long id,
            @RequestParam long version,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", service.disableRule(
                id, version, principal.userId(), AuditRequestContext.from(httpRequest)));
    }
}
