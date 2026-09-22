package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.OrganizationDomainChallengeResponse;
import com.iflytek.skillhub.dto.OrganizationDomainCreateRequest;
import com.iflytek.skillhub.dto.OrganizationDomainResponse;
import com.iflytek.skillhub.dto.OrganizationMemberCreateRequest;
import com.iflytek.skillhub.dto.OrganizationMemberResponse;
import com.iflytek.skillhub.dto.OrganizationResponse;
import com.iflytek.skillhub.dto.OrganizationRoleBindingCreateRequest;
import com.iflytek.skillhub.dto.OrganizationRoleBindingResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.OrganizationAdminAppService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tenant administration transport; Organization authorization remains in the application/domain. */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationAdminController extends BaseApiController {

    private final OrganizationAdminAppService appService;

    public OrganizationAdminController(
            OrganizationAdminAppService appService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.appService = appService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrganizationResponse>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", appService.listMine(principal.userId(), page, size));
    }

    @GetMapping("/{organizationId}")
    public ApiResponse<OrganizationResponse> get(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", appService.get(organizationId, principal.userId()));
    }

    @PostMapping("/{organizationId}/suspend")
    public ApiResponse<OrganizationResponse> suspend(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", appService.suspend(organizationId, principal.userId()));
    }

    @PostMapping("/{organizationId}/reactivate")
    public ApiResponse<OrganizationResponse> reactivate(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", appService.reactivate(organizationId, principal.userId()));
    }

    @PostMapping("/{organizationId}/decommission")
    public ApiResponse<OrganizationResponse> decommission(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.decommission(organizationId, principal.userId())
        );
    }

    @GetMapping("/{organizationId}/domains")
    public ApiResponse<PageResponse<OrganizationDomainResponse>> listDomains(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.read",
                appService.listDomains(organizationId, principal.userId(), page, size)
        );
    }

    @PostMapping("/{organizationId}/domains")
    public ApiResponse<OrganizationDomainChallengeResponse> issueDomainChallenge(
            @PathVariable String organizationId,
            @Valid @RequestBody OrganizationDomainCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.created",
                appService.issueDomainChallenge(organizationId, request, principal.userId())
        );
    }

    @PostMapping("/{organizationId}/domains/{domainId}/verify")
    public ApiResponse<OrganizationDomainResponse> verifyDomain(
            @PathVariable String organizationId,
            @PathVariable String domainId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.verifyDomain(organizationId, domainId, principal.userId())
        );
    }

    @PostMapping("/{organizationId}/domains/{domainId}/disable")
    public ApiResponse<OrganizationDomainResponse> disableDomain(
            @PathVariable String organizationId,
            @PathVariable String domainId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.disableDomain(organizationId, domainId, principal.userId())
        );
    }

    @GetMapping("/{organizationId}/members")
    public ApiResponse<PageResponse<OrganizationMemberResponse>> listMembers(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.read",
                appService.listMembers(organizationId, principal.userId(), page, size)
        );
    }

    @PostMapping("/{organizationId}/members")
    public ApiResponse<OrganizationMemberResponse> addMember(
            @PathVariable String organizationId,
            @Valid @RequestBody OrganizationMemberCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.created",
                appService.addMember(organizationId, request, principal.userId())
        );
    }

    @PostMapping("/{organizationId}/members/{membershipId}/suspend")
    public ApiResponse<OrganizationMemberResponse> suspendMember(
            @PathVariable String organizationId,
            @PathVariable String membershipId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.suspendMember(organizationId, membershipId, principal.userId())
        );
    }

    @PostMapping("/{organizationId}/members/{membershipId}/reactivate")
    public ApiResponse<OrganizationMemberResponse> reactivateMember(
            @PathVariable String organizationId,
            @PathVariable String membershipId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.reactivateMember(organizationId, membershipId, principal.userId())
        );
    }

    @PostMapping("/{organizationId}/members/{membershipId}/deprovision")
    public ApiResponse<OrganizationMemberResponse> deprovisionMember(
            @PathVariable String organizationId,
            @PathVariable String membershipId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.deprovisionMember(organizationId, membershipId, principal.userId())
        );
    }

    @GetMapping("/{organizationId}/role-bindings")
    public ApiResponse<PageResponse<OrganizationRoleBindingResponse>> listRoleBindings(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.read",
                appService.listRoleBindings(organizationId, principal.userId(), page, size)
        );
    }

    @PostMapping("/{organizationId}/role-bindings")
    public ApiResponse<OrganizationRoleBindingResponse> grantRole(
            @PathVariable String organizationId,
            @Valid @RequestBody OrganizationRoleBindingCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.created",
                appService.grantRole(organizationId, request, principal.userId())
        );
    }

    @DeleteMapping("/{organizationId}/role-bindings/{bindingId}")
    public ApiResponse<OrganizationRoleBindingResponse> revokeRole(
            @PathVariable String organizationId,
            @PathVariable String bindingId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.revokeRole(organizationId, bindingId, principal.userId())
        );
    }
}
