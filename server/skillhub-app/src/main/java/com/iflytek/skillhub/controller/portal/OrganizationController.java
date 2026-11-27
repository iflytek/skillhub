package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.LoginConnectionSummaryResponse;
import com.iflytek.skillhub.dto.OrganizationDetailResponse;
import com.iflytek.skillhub.dto.OrganizationMemberRequest;
import com.iflytek.skillhub.dto.OrganizationMemberResponse;
import com.iflytek.skillhub.dto.OrganizationRoleBindingResponse;
import com.iflytek.skillhub.dto.OrganizationRoleGrantRequest;
import com.iflytek.skillhub.dto.OrganizationSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.OrganizationMemberAdminAppService;
import com.iflytek.skillhub.service.OrganizationPortalQueryAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@PreAuthorize("isAuthenticated()")
public class OrganizationController extends BaseApiController {

    private final OrganizationPortalQueryAppService organizationQueryAppService;
    private final OrganizationMemberAdminAppService memberAdminAppService;

    public OrganizationController(
            OrganizationPortalQueryAppService organizationQueryAppService,
            OrganizationMemberAdminAppService memberAdminAppService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.organizationQueryAppService = organizationQueryAppService;
        this.memberAdminAppService = memberAdminAppService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrganizationSummaryResponse>> listOrganizations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.read",
                organizationQueryAppService.listOrganizations(principal.userId(), page, size)
        );
    }

    @GetMapping("/{organizationId}")
    public ApiResponse<OrganizationDetailResponse> getOrganization(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.read",
                organizationQueryAppService.getOrganization(organizationId, principal.userId())
        );
    }

    @GetMapping("/{organizationId}/login-connections")
    public ApiResponse<PageResponse<LoginConnectionSummaryResponse>> listLoginConnections(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.read",
                organizationQueryAppService.listLoginConnections(
                        organizationId,
                        principal.userId(),
                        page,
                        size
                )
        );
    }

    @GetMapping("/{organizationId}/members")
    public ApiResponse<PageResponse<OrganizationMemberResponse>> listOrganizationMembers(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", memberAdminAppService.listMembers(
                organizationId, principal.userId(), page, size));
    }

    @PostMapping("/{organizationId}/members")
    public ApiResponse<OrganizationMemberResponse> addOrganizationMember(
            @PathVariable String organizationId,
            @Valid @RequestBody OrganizationMemberRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created", memberAdminAppService.addMember(
                organizationId, request.userId(), principal.userId()));
    }

    @PostMapping("/{organizationId}/members/{userId}/roles")
    public ApiResponse<OrganizationRoleBindingResponse> grantOrganizationRole(
            @PathVariable String organizationId,
            @PathVariable String userId,
            @Valid @RequestBody OrganizationRoleGrantRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created", memberAdminAppService.grantRole(
                organizationId, userId, request.role(), principal.userId()));
    }

    @DeleteMapping("/{organizationId}/role-bindings/{bindingId}")
    public ApiResponse<OrganizationRoleBindingResponse> revokeOrganizationRole(
            @PathVariable String organizationId,
            @PathVariable String bindingId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", memberAdminAppService.revokeRole(
                organizationId, bindingId, principal.userId()));
    }
}
