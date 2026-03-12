package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.AdminUserMutationResponse;
import com.iflytek.skillhub.dto.AdminUserRoleUpdateRequest;
import com.iflytek.skillhub.dto.AdminUserStatusUpdateRequest;
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManagementController extends BaseApiController {

    public UserManagementController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AdminUserSummaryResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<AdminUserSummaryResponse> users = List.of(
                new AdminUserSummaryResponse("user-1", "alice", "USER", "ACTIVE"),
                new AdminUserSummaryResponse("user-2", "bob", "USER", "ACTIVE")
        );
        return ok("response.success.read", PageResponse.from(new PageImpl<>(users)));
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserRoleUpdateRequest request) {
        return ok("response.success.updated", new AdminUserMutationResponse(userId, request.role(), null));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserMutationResponse> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ok("response.success.updated", new AdminUserMutationResponse(userId, null, request.status()));
    }
}
