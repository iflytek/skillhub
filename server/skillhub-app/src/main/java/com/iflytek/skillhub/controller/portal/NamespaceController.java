package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/namespaces", "/api/web/namespaces"})
public class NamespaceController extends BaseApiController {

    private final NamespaceService namespaceService;
    private final NamespaceMemberService namespaceMemberService;
    private final NamespaceRepository namespaceRepository;

    public NamespaceController(NamespaceService namespaceService,
                              NamespaceMemberService namespaceMemberService,
                              NamespaceRepository namespaceRepository,
                              ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.namespaceService = namespaceService;
        this.namespaceMemberService = namespaceMemberService;
        this.namespaceRepository = namespaceRepository;
    }

    @GetMapping
    public ApiResponse<PageResponse<NamespaceResponse>> listNamespaces(Pageable pageable) {
        Page<Namespace> namespaces = namespaceRepository.findByStatus(NamespaceStatus.ACTIVE, pageable);
        PageResponse<NamespaceResponse> response = PageResponse.from(namespaces.map(NamespaceResponse::from));
        return ok("response.success.read", response);
    }

    @GetMapping("/{slug}")
    public ApiResponse<NamespaceResponse> getNamespace(@PathVariable String slug) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        return ok("response.success.read", NamespaceResponse.from(namespace));
    }

    @PostMapping
    public ApiResponse<NamespaceResponse> createNamespace(
            @Valid @RequestBody NamespaceRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Namespace namespace = namespaceService.createNamespace(
                request.slug(),
                request.displayName(),
                request.description(),
                principal.userId()
        );
        return ok("response.success.created", NamespaceResponse.from(namespace));
    }

    @PutMapping("/{slug}")
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

    @GetMapping("/{slug}/members")
    public ApiResponse<PageResponse<MemberResponse>> listMembers(@PathVariable String slug, Pageable pageable) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        Page<NamespaceMember> members = namespaceMemberService.listMembers(namespace.getId(), pageable);
        PageResponse<MemberResponse> response = PageResponse.from(members.map(MemberResponse::from));
        return ok("response.success.read", response);
    }

    @PostMapping("/{slug}/members")
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

    @DeleteMapping("/{slug}/members/{userId}")
    public ApiResponse<MessageResponse> removeMember(
            @PathVariable String slug,
            @PathVariable("userId") String memberUserId,
            @RequestAttribute("userId") String operatorUserId) {
        Namespace namespace = namespaceService.getNamespaceBySlug(slug);
        namespaceMemberService.removeMember(namespace.getId(), memberUserId, operatorUserId);
        return ok("response.success.deleted", new MessageResponse("Member removed successfully"));
    }

    @PutMapping("/{slug}/members/{userId}/role")
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
