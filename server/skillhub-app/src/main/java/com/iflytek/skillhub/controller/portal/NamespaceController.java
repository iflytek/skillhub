package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.exception.ForbiddenException;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.NamespaceMemberCandidateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Namespace portal endpoints for discovery, membership management, and
 * namespace governance operations.
 */
@RestController
@RequestMapping({"/api/v1", "/api/web"})
public class NamespaceController extends BaseApiController {

    private final NamespaceService namespaceService;
    private final NamespaceMemberService namespaceMemberService;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceGovernanceService namespaceGovernanceService;
    private final NamespaceAccessPolicy namespaceAccessPolicy;
    private final NamespaceMemberCandidateService namespaceMemberCandidateService;

    public NamespaceController(NamespaceService namespaceService,
                              NamespaceMemberService namespaceMemberService,
                              NamespaceRepository namespaceRepository,
                              NamespaceGovernanceService namespaceGovernanceService,
                              NamespaceAccessPolicy namespaceAccessPolicy,
                              NamespaceMemberCandidateService namespaceMemberCandidateService,
                              ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.namespaceService = namespaceService;
        this.namespaceMemberService = namespaceMemberService;
        this.namespaceRepository = namespaceRepository;
        this.namespaceGovernanceService = namespaceGovernanceService;
        this.namespaceAccessPolicy = namespaceAccessPolicy;
        this.namespaceMemberCandidateService = namespaceMemberCandidateService;
    }

    @GetMapping("/namespaces")
    public ApiResponse<PageResponse<NamespaceResponse>> listNamespaces(Pageable pageable) {
        Page<Namespace> namespaces = namespaceRepository.findByStatus(NamespaceStatus.ACTIVE, pageable);
        PageResponse<NamespaceResponse> response = PageResponse.from(namespaces.map(NamespaceResponse::from));
        return ok("response.success.read", response);
    }

    @GetMapping("/me/namespaces")
    public ApiResponse<List<MyNamespaceResponse>> listMyNamespaces(
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        Map<Long, NamespaceRole> namespaceRoles = userNsRoles != null ? userNsRoles : Map.of();
        if (namespaceRoles.isEmpty()) {
            return ok("response.success.read", List.of());
        }

        List<MyNamespaceResponse> response = namespaceRepository.findByIdIn(namespaceRoles.keySet().stream().toList()).stream()
                .sorted(Comparator.comparing(Namespace::getSlug))
                .map(namespace -> MyNamespaceResponse.from(namespace, namespaceRoles.get(namespace.getId()), namespaceAccessPolicy))
                .toList();

        return ok("response.success.read", response);
    }

    @GetMapping("/namespaces/{slug}")
    public ApiResponse<NamespaceResponse> getNamespace(@PathVariable String slug,
                                                       @RequestAttribute(value = "userId", required = false) String userId,
                                                       @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = namespaceService.getNamespaceBySlugForRead(slug, userId, userNsRoles != null ? userNsRoles : Map.of());
        return ok("response.success.read", NamespaceResponse.from(namespace));
    }

    @PostMapping("/namespaces")
    public ApiResponse<NamespaceResponse> createNamespace(
            @Valid @RequestBody NamespaceRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        if (!canCreateNamespace(principal)) {
            throw new ForbiddenException("error.namespace.create.platformAdminRequired");
        }

        Namespace namespace = namespaceService.createNamespace(
                request.slug(),
                request.displayName(),
                request.description(),
                principal.userId()
        );
        return ok("response.success.created", NamespaceResponse.from(namespace));
    }

    private boolean canCreateNamespace(PlatformPrincipal principal) {
        return principal.platformRoles().contains("SKILL_ADMIN")
                || principal.platformRoles().contains("SUPER_ADMIN");
    }

    @PutMapping("/namespaces/{slug}")
    public ApiResponse<NamespaceResponse> updateNamespace(
            @PathVariable String slug,
            @RequestBody NamespaceRequest request,
            @RequestAttribute("userId") String userId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Namespace updated = namespaceService.updateNamespace(
                namespace.getId(),
                request.displayName(),
                request.description(),
                null,
                userId
        );
        return ok("response.success.updated", NamespaceResponse.from(updated));
    }

    @PostMapping("/namespaces/{slug}/freeze")
    public ApiResponse<NamespaceResponse> freezeNamespace(@PathVariable String slug,
                                                          @RequestBody(required = false) NamespaceLifecycleRequest request,
                                                          @RequestAttribute("userId") String userId,
                                                          HttpServletRequest httpRequest) {
        Namespace namespace = namespaceGovernanceService.freezeNamespace(
                slug,
                userId,
                request != null ? request.reason() : null,
                null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", NamespaceResponse.from(namespace));
    }

    @PostMapping("/namespaces/{slug}/unfreeze")
    public ApiResponse<NamespaceResponse> unfreezeNamespace(@PathVariable String slug,
                                                            @RequestAttribute("userId") String userId,
                                                            HttpServletRequest httpRequest) {
        Namespace namespace = namespaceGovernanceService.unfreezeNamespace(
                slug,
                userId,
                null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", NamespaceResponse.from(namespace));
    }

    @PostMapping("/namespaces/{slug}/archive")
    public ApiResponse<NamespaceResponse> archiveNamespace(@PathVariable String slug,
                                                           @RequestBody(required = false) NamespaceLifecycleRequest request,
                                                           @RequestAttribute("userId") String userId,
                                                           HttpServletRequest httpRequest) {
        Namespace namespace = namespaceGovernanceService.archiveNamespace(
                slug,
                userId,
                request != null ? request.reason() : null,
                null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", NamespaceResponse.from(namespace));
    }

    @PostMapping("/namespaces/{slug}/restore")
    public ApiResponse<NamespaceResponse> restoreNamespace(@PathVariable String slug,
                                                           @RequestAttribute("userId") String userId,
                                                           HttpServletRequest httpRequest) {
        Namespace namespace = namespaceGovernanceService.restoreNamespace(
                slug,
                userId,
                null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", NamespaceResponse.from(namespace));
    }

    @GetMapping("/namespaces/{slug}/members")
    public ApiResponse<PageResponse<MemberResponse>> listMembers(@PathVariable String slug,
                                                                 Pageable pageable,
                                                                 @RequestAttribute("userId") String userId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        namespaceService.assertMember(namespace.getId(), userId);
        Page<NamespaceMember> members = namespaceMemberService.listMembers(namespace.getId(), pageable);
        PageResponse<MemberResponse> response = PageResponse.from(members.map(MemberResponse::from));
        return ok("response.success.read", response);
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
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        NamespaceMember member = namespaceMemberService.addMember(
                namespace.getId(),
                request.userId(),
                request.role(),
                userId
        );
        return ok("response.success.created", MemberResponse.from(member));
    }

    @DeleteMapping("/namespaces/{slug}/members/{userId}")
    public ApiResponse<MessageResponse> removeMember(
            @PathVariable String slug,
            @PathVariable("userId") String memberUserId,
            @RequestAttribute("userId") String operatorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        namespaceMemberService.removeMember(namespace.getId(), memberUserId, operatorUserId);
        return ok("response.success.deleted", new MessageResponse("Member removed successfully"));
    }

    @PutMapping("/namespaces/{slug}/members/{userId}/role")
    public ApiResponse<MemberResponse> updateMemberRole(
            @PathVariable String slug,
            @PathVariable String userId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @RequestAttribute("userId") String operatorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        NamespaceMember member = namespaceMemberService.updateMemberRole(
                namespace.getId(),
                userId,
                request.role(),
                operatorUserId
        );
        return ok("response.success.updated", MemberResponse.from(member));
    }
}
