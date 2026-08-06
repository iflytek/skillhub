package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
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
import com.iflytek.skillhub.dto.TransferOwnershipRequest;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.GovernanceWorkflowAppService;
import com.iflytek.skillhub.service.NamespacePortalCommandAppService;
import com.iflytek.skillhub.service.NamespacePortalQueryAppService;
import com.iflytek.skillhub.service.NamespaceMemberCandidateService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read",
                namespacePortalQueryAppService.listNamespaces(pageable, userNsRoles, normalizePlatformRoles(platformRoles)));
    }

    @GetMapping("/me/namespaces")
    public ApiResponse<List<MyNamespaceResponse>> listMyNamespaces(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read",
                namespacePortalQueryAppService.listMyNamespaces(userNsRoles, normalizePlatformRoles(platformRoles)));
    }

    @GetMapping("/me/namespaces/page")
    public ApiResponse<PageResponse<MyNamespaceResponse>> listMyNamespacesPage(
            @Parameter(description = "Zero-based page index.", schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size. Values above the namespace list limit are bounded by the backend.", schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1"))
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort criteria in property,direction form. Only slug sorting is honored; defaults to slug,asc.", example = "slug,asc")
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) NamespaceStatus status,
            @RequestParam(required = false) NamespaceType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) Set<NamespaceRole> roles,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read",
                namespacePortalQueryAppService.listMyNamespaces(
                        myNamespacesPageable(page, size, sort),
                        userNsRoles,
                        normalizePlatformRoles(platformRoles),
                        status,
                        type,
                        q,
                        slug,
                        roles));
    }

    @GetMapping("/namespaces/{slug}")
    public ApiResponse<NamespaceResponse> getNamespace(@PathVariable String slug,
                                                       @RequestAttribute("userId") String userId,
                                                       @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                       @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read",
                namespacePortalQueryAppService.getNamespace(slug, userId, userNsRoles, normalizePlatformRoles(platformRoles)));
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

    @DeleteMapping("/namespaces/{slug}")
    public ApiResponse<MessageResponse> deleteNamespace(
            @PathVariable String slug,
            @RequestAttribute("userId") String userId) {
        return ok("response.success.deleted",
                namespacePortalCommandAppService.deleteNamespace(slug, userId));
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
                                                                 @RequestAttribute("userId") String userId,
                                                                 @AuthenticationPrincipal PlatformPrincipal principal) {
        Set<String> platformRoles = principal != null && principal.platformRoles() != null
                ? principal.platformRoles()
                : Set.of();
        return ok("response.success.read",
                namespacePortalQueryAppService.listMembers(slug, pageable, userId, platformRoles));
    }

    private Set<String> normalizePlatformRoles(Set<String> platformRoles) {
        return platformRoles != null
                ? platformRoles
                : Set.of();
    }

    private Pageable myNamespacesPageable(int page, int size, List<String> sort) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1),
                myNamespacesSort(sort)
        );
    }

    private Sort myNamespacesSort(List<String> sort) {
        if (sort == null || sort.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String rawSort : sort) {
            Sort.Order order = slugOrder(rawSort);
            if (order != null) {
                orders.add(order);
            }
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }

    private Sort.Order slugOrder(String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return null;
        }
        String[] tokens = rawSort.split(",");
        if (!"slug".equals(tokens[0].trim())) {
            return null;
        }
        Sort.Direction direction = tokens.length > 1
                ? Sort.Direction.fromOptionalString(tokens[1].trim()).orElse(Sort.Direction.ASC)
                : Sort.Direction.ASC;
        return new Sort.Order(direction, "slug");
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

    @PostMapping("/namespaces/{slug}/transfer-ownership")
    public ApiResponse<MessageResponse> transferOwnership(
            @PathVariable String slug,
            @Valid @RequestBody TransferOwnershipRequest request,
            @RequestAttribute("userId") String currentOwnerId) {
        return ok("response.success.updated",
                namespacePortalCommandAppService.transferOwnership(slug, request.newOwnerId(), currentOwnerId));
    }
}
