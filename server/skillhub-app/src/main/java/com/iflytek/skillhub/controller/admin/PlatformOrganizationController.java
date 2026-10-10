package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.OrganizationCreateRequest;
import com.iflytek.skillhub.dto.OrganizationCreateResponse;
import com.iflytek.skillhub.service.PlatformOrganizationCreateAppService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/organizations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformOrganizationController extends BaseApiController {

    private final PlatformOrganizationCreateAppService appService;

    public PlatformOrganizationController(
            PlatformOrganizationCreateAppService appService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.appService = appService;
    }

    @PostMapping
    @Operation(operationId = "createOrganization", summary = "Create an organization with an initial owner")
    public ApiResponse<OrganizationCreateResponse> create(
            @Valid @RequestBody OrganizationCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created", appService.create(request, principal.userId()));
    }
}
