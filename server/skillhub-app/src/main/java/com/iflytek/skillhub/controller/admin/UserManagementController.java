package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.local.PasswordResetService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.AdminUserMutationResponse;
import com.iflytek.skillhub.dto.AdminUserRoleUpdateRequest;
import com.iflytek.skillhub.dto.AdminUserStatusUpdateRequest;
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.AdminUserAppService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative endpoints for listing users and mutating user roles or
 * account status.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManagementController extends BaseApiController {

    private final AdminUserAppService adminUserAppService;
    private final PasswordResetService passwordResetService;

    public UserManagementController(AdminUserAppService adminUserAppService,
                                    PasswordResetService passwordResetService,
                                    ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminUserAppService = adminUserAppService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AdminUserSummaryResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", adminUserAppService.listUsers(search, status, page, size));
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> updateUserRole(
            @PathVariable String userId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @Valid @RequestBody AdminUserRoleUpdateRequest request) {
        return ok("response.success.updated",
                adminUserAppService.updateUserRole(userId, request.role(), principal.platformRoles()));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ok("response.success.updated", adminUserAppService.updateUserStatus(userId, request.status()));
    }

    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> approveUser(@PathVariable String userId) {
        return ok("response.success.updated", adminUserAppService.updateUserStatus(userId, "ACTIVE"));
    }

    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> disableUser(@PathVariable String userId) {
        return ok("response.success.updated", adminUserAppService.updateUserStatus(userId, "DISABLED"));
    }

    @PostMapping("/{userId}/enable")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> enableUser(@PathVariable String userId) {
        return ok("response.success.updated", adminUserAppService.updateUserStatus(userId, "ACTIVE"));
    }

    @PostMapping("/{userId}/password-reset")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Void> triggerPasswordReset(@PathVariable String userId,
                                                  @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        passwordResetService.adminTriggerPasswordReset(userId, principal.userId());
        return ok("response.auth.password.reset.requested", null);
    }
}
