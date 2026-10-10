package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.LoginConnectionSummaryResponse;
import com.iflytek.skillhub.dto.OrganizationDetailResponse;
import com.iflytek.skillhub.dto.OrganizationSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.OrganizationPortalQueryAppService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@PreAuthorize("isAuthenticated()")
public class OrganizationController extends BaseApiController {

    private final OrganizationPortalQueryAppService organizationQueryAppService;

    public OrganizationController(
            OrganizationPortalQueryAppService organizationQueryAppService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.organizationQueryAppService = organizationQueryAppService;
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
}
