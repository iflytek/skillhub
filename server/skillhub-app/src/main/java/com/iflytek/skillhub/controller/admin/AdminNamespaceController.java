package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.AdminNamespaceDetailResponse;
import com.iflytek.skillhub.dto.AdminNamespaceListResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.BatchMemberRequest;
import com.iflytek.skillhub.dto.BatchMemberResponse;
import com.iflytek.skillhub.dto.MemberRequest;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.TransferOwnershipRequest;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.service.AdminNamespaceAppService;
import com.iflytek.skillhub.service.AuditRequestContext;
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
@RequestMapping("/api/v1/admin/namespaces")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminNamespaceController extends BaseApiController {

    private final AdminNamespaceAppService adminNamespaceAppService;

    public AdminNamespaceController(AdminNamespaceAppService adminNamespaceAppService,
                                    ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminNamespaceAppService = adminNamespaceAppService;
    }

    @GetMapping
    public ApiResponse<AdminNamespaceListResponse> listNamespaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read",
                adminNamespaceAppService.list(keyword, status, type, page, size, principal.userId()));
    }

    @GetMapping("/{slug}")
    public ApiResponse<AdminNamespaceDetailResponse> getNamespace(@PathVariable String slug,
                                                                  @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", adminNamespaceAppService.detail(slug, principal.userId()));
    }

    @GetMapping("/{slug}/members")
    public ApiResponse<PageResponse<MemberResponse>> listMembers(@PathVariable String slug,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", adminNamespaceAppService.listMembers(slug, page, size));
    }

    @GetMapping("/{slug}/member-candidates")
    public ApiResponse<List<NamespaceCandidateUserResponse>> searchMemberCandidates(
            @PathVariable String slug,
            @RequestParam String search,
            @RequestParam(defaultValue = "10") int size) {
        return ok("response.success.read", adminNamespaceAppService.searchMemberCandidates(slug, search, size));
    }

    @PostMapping("/{slug}/members")
    public ApiResponse<MemberResponse> addMember(@PathVariable String slug,
                                                 @Valid @RequestBody MemberRequest request,
                                                 @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created", adminNamespaceAppService.addMember(slug, request, principal.userId()));
    }

    @PostMapping("/{slug}/members/batch")
    public ApiResponse<BatchMemberResponse> batchAddMembers(@PathVariable String slug,
                                                            @Valid @RequestBody BatchMemberRequest request,
                                                            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created", adminNamespaceAppService.batchAddMembers(slug, request, principal.userId()));
    }

    @PutMapping("/{slug}/members/{userId}/role")
    public ApiResponse<MemberResponse> updateMemberRole(@PathVariable String slug,
                                                        @PathVariable String userId,
                                                        @Valid @RequestBody UpdateMemberRoleRequest request,
                                                        @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", adminNamespaceAppService.updateMemberRole(slug, userId, request, principal.userId()));
    }

    @DeleteMapping("/{slug}/members/{userId}")
    public ApiResponse<MessageResponse> removeMember(@PathVariable String slug,
                                                     @PathVariable String userId,
                                                     @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.deleted", adminNamespaceAppService.removeMember(slug, userId, principal.userId()));
    }

    @PostMapping("/{slug}/transfer-ownership")
    public ApiResponse<MessageResponse> transferOwnership(@PathVariable String slug,
                                                          @Valid @RequestBody TransferOwnershipRequest request,
                                                          @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", adminNamespaceAppService.transferOwnership(slug, request.newOwnerId(), principal.userId()));
    }

    @PostMapping("/{slug}/freeze")
    public ApiResponse<AdminNamespaceDetailResponse> freezeNamespace(@PathVariable String slug,
                                                                     @RequestBody(required = false) NamespaceLifecycleRequest request,
                                                                     @AuthenticationPrincipal PlatformPrincipal principal,
                                                                     HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                adminNamespaceAppService.freeze(slug, request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/{slug}/unfreeze")
    public ApiResponse<AdminNamespaceDetailResponse> unfreezeNamespace(@PathVariable String slug,
                                                                       @AuthenticationPrincipal PlatformPrincipal principal,
                                                                       HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                adminNamespaceAppService.unfreeze(slug, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/{slug}/archive")
    public ApiResponse<AdminNamespaceDetailResponse> archiveNamespace(@PathVariable String slug,
                                                                      @RequestBody(required = false) NamespaceLifecycleRequest request,
                                                                      @AuthenticationPrincipal PlatformPrincipal principal,
                                                                      HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                adminNamespaceAppService.archive(slug, request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/{slug}/restore")
    public ApiResponse<AdminNamespaceDetailResponse> restoreNamespace(@PathVariable String slug,
                                                                      @AuthenticationPrincipal PlatformPrincipal principal,
                                                                      HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                adminNamespaceAppService.restore(slug, principal.userId(), AuditRequestContext.from(httpRequest)));
    }
}
