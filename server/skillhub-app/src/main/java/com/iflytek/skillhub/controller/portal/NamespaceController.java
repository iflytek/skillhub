package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.BatchMemberRequest;
import com.iflytek.skillhub.dto.BatchMemberResponse;
import com.iflytek.skillhub.dto.MemberRequest;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.MyNamespaceResponse;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceRequest;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.GovernanceWorkflowAppService;
import com.iflytek.skillhub.service.NamespacePortalCommandAppService;
import com.iflytek.skillhub.service.NamespacePortalQueryAppService;
import com.iflytek.skillhub.service.NamespaceMemberCandidateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Namespace portal endpoints for discovery, membership management, and
 * namespace governance operations.
 */
@RestController
@RequestMapping({"/api/v1", "/api/web"})
public class NamespaceController extends BaseApiController {

    private final NamespacePortalQueryAppService namespacePortalQueryAppService;
    private final NamespacePortalCommandAppService namespacePortalCommandAppService;
    private final NamespaceMemberCandidateService namespaceMemberCandidateService;
    private final GovernanceWorkflowAppService governanceWorkflowAppService;

    public NamespaceController(NamespacePortalQueryAppService namespacePortalQueryAppService,
                               NamespacePortalCommandAppService namespacePortalCommandAppService,
                               NamespaceMemberCandidateService namespaceMemberCandidateService,
                               GovernanceWorkflowAppService governanceWorkflowAppService,
                               ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.namespacePortalQueryAppService = namespacePortalQueryAppService;
        this.namespacePortalCommandAppService = namespacePortalCommandAppService;
        this.namespaceMemberCandidateService = namespaceMemberCandidateService;
        this.governanceWorkflowAppService = governanceWorkflowAppService;
    }

    @GetMapping("/namespaces")
    public ApiResponse<PageResponse<NamespaceResponse>> listNamespaces(
            Pageable pageable,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read", namespacePortalQueryAppService.listNamespaces(pageable, userNsRoles));
    }

    @GetMapping("/me/namespaces")
    public ApiResponse<List<MyNamespaceResponse>> listMyNamespaces(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read", namespacePortalQueryAppService.listMyNamespaces(userNsRoles));
    }

    @GetMapping("/namespaces/{slug}")
    public ApiResponse<NamespaceResponse> getNamespace(@PathVariable String slug,
                                                       @RequestAttribute("userId") String userId,
                                                       @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read",
                namespacePortalQueryAppService.getNamespace(slug, userId, userNsRoles));
    }

    @PostMapping("/namespaces")
    public ApiResponse<NamespaceResponse> createNamespace(
            @Valid @RequestBody NamespaceRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created",
                namespacePortalCommandAppService.createNamespace(request, principal));
    }

    @PutMapping("/namespaces/{slug}")
    public ApiResponse<NamespaceResponse> updateNamespace(
            @PathVariable String slug,
            @RequestBody NamespaceRequest request,
            @RequestAttribute("userId") String userId) {
        return ok("response.success.updated",
                namespacePortalCommandAppService.updateNamespace(slug, request, userId));
    }

    @PostMapping("/namespaces/{slug}/freeze")
    public ApiResponse<NamespaceResponse> freezeNamespace(@PathVariable String slug,
                                                          @RequestBody(required = false) NamespaceLifecycleRequest request,
                                                          @RequestAttribute("userId") String userId,
                                                          HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                governanceWorkflowAppService.freezeNamespace(
                        slug,
                        request,
                        userId,
                        AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/namespaces/{slug}/unfreeze")
    public ApiResponse<NamespaceResponse> unfreezeNamespace(@PathVariable String slug,
                                                            @RequestAttribute("userId") String userId,
                                                            HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                governanceWorkflowAppService.unfreezeNamespace(
                        slug,
                        userId,
                        AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/namespaces/{slug}/archive")
    public ApiResponse<NamespaceResponse> archiveNamespace(@PathVariable String slug,
                                                           @RequestBody(required = false) NamespaceLifecycleRequest request,
                                                           @RequestAttribute("userId") String userId,
                                                           HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                governanceWorkflowAppService.archiveNamespace(
                        slug,
                        request,
                        userId,
                        AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/namespaces/{slug}/restore")
    public ApiResponse<NamespaceResponse> restoreNamespace(@PathVariable String slug,
                                                           @RequestAttribute("userId") String userId,
                                                           HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                governanceWorkflowAppService.restoreNamespace(
                        slug,
                        userId,
                        AuditRequestContext.from(httpRequest)));
    }

    @GetMapping("/namespaces/{slug}/members")
    public ApiResponse<PageResponse<MemberResponse>> listMembers(@PathVariable String slug,
                                                                 Pageable pageable,
                                                                 @RequestAttribute("userId") String userId) {
        return ok("response.success.read",
                namespacePortalQueryAppService.listMembers(slug, pageable, userId));
    }

    @GetMapping("/namespaces/{slug}/member-candidates")
    public ApiResponse<List<NamespaceCandidateUserResponse>> searchMemberCandidates(
            @PathVariable String slug,
            @RequestParam String search,
            @RequestParam(defaultValue = "10") int size,
            @RequestAttribute("userId") String userId) {
        return ok("response.success.read", namespaceMemberCandidateService.searchCandidates(slug, search, userId, size));
    }

    @PostMapping("/namespaces/{slug}/members")
    public ApiResponse<MemberResponse> addMember(
            @PathVariable String slug,
            @Valid @RequestBody MemberRequest request,
            @RequestAttribute("userId") String userId) {
        return ok("response.success.created",
                namespacePortalCommandAppService.addMember(slug, request.userId(), request.role(), userId));
    }

    @PostMapping("/namespaces/{slug}/members/batch")
    public ApiResponse<BatchMemberResponse> batchAddMembers(
            @PathVariable String slug,
            @Valid @RequestBody BatchMemberRequest request,
            @RequestAttribute("userId") String userId) {
        return ok("response.success.created",
                namespacePortalCommandAppService.batchAddMembers(slug, request.members(), userId));
    }

    @DeleteMapping("/namespaces/{slug}/members/{userId}")
    public ApiResponse<MessageResponse> removeMember(
            @PathVariable String slug,
            @PathVariable("userId") String memberUserId,
            @RequestAttribute("userId") String operatorUserId) {
        return ok("response.success.deleted",
                namespacePortalCommandAppService.removeMember(slug, memberUserId, operatorUserId));
    }

    @PutMapping("/namespaces/{slug}/members/{userId}/role")
    public ApiResponse<MemberResponse> updateMemberRole(
            @PathVariable String slug,
            @PathVariable String userId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @RequestAttribute("userId") String operatorUserId) {
        return ok("response.success.updated",
                namespacePortalCommandAppService.updateMemberRole(slug, userId, request, operatorUserId));
    }
}
