package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.OrganizationCreateRequest;
import com.iflytek.skillhub.dto.OrganizationResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.PlatformOrganizationAdminAppService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit platform-only Organization creation and inventory surface. */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformOrganizationAdminController extends BaseApiController {

    private final PlatformOrganizationAdminAppService appService;

    public PlatformOrganizationAdminController(
            PlatformOrganizationAdminAppService appService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.appService = appService;
    }

    @PostMapping
    public ApiResponse<OrganizationResponse> create(
            @Valid @RequestBody OrganizationCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.created",
                appService.create(request, principal.userId())
        );
    }

    @GetMapping
    public ApiResponse<PageResponse<OrganizationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ok("response.success.read", appService.list(page, size));
    }
}
