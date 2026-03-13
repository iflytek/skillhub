package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.AdminUserMutationResponse;
import com.iflytek.skillhub.dto.AdminUserRoleUpdateRequest;
import com.iflytek.skillhub.dto.AdminUserStatusUpdateRequest;
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminUserManagementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManagementController extends BaseApiController {

    private final AdminUserManagementService adminUserManagementService;

    public UserManagementController(ApiResponseFactory responseFactory,
                                    AdminUserManagementService adminUserManagementService) {
        super(responseFactory);
        this.adminUserManagementService = adminUserManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AdminUserSummaryResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", adminUserManagementService.listUsers(search, status, Math.max(0, page - 1), size));
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserRoleUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        AdminUserSummaryResponse user = adminUserManagementService.updateUserRole(userId, request.role(), principal);
        return ok("response.success.updated", new AdminUserMutationResponse(user.userId(), request.role(), user.status()));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        AdminUserSummaryResponse user = adminUserManagementService.updateUserStatus(userId, request.status());
        return ok("response.success.updated", new AdminUserMutationResponse(user.userId(), null, user.status()));
    }

    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> approveUser(@PathVariable String userId) {
        AdminUserSummaryResponse user = adminUserManagementService.approveUser(userId);
        return ok("response.success.updated", new AdminUserMutationResponse(user.userId(), null, user.status()));
    }

    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> disableUser(@PathVariable String userId) {
        AdminUserSummaryResponse user = adminUserManagementService.disableUser(userId);
        return ok("response.success.updated", new AdminUserMutationResponse(user.userId(), null, user.status()));
    }

    @PostMapping("/{userId}/enable")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> enableUser(@PathVariable String userId) {
        AdminUserSummaryResponse user = adminUserManagementService.enableUser(userId);
        return ok("response.success.updated", new AdminUserMutationResponse(user.userId(), null, user.status()));
    }
}
