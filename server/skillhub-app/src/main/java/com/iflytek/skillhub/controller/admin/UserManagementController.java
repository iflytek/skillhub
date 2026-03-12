package com.iflytek.skillhub.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManagementController {

    @GetMapping
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Placeholder implementation
        return Map.of(
            "users", List.of(
                Map.of("id", "user-1", "username", "alice", "role", "USER", "status", "ACTIVE"),
                Map.of("id", "user-2", "username", "bob", "role", "USER", "status", "ACTIVE")
            ),
            "total", 2,
            "page", page,
            "size", size
        );
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> updateUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        String newRole = request.get("role");
        // Placeholder implementation
        return Map.of(
            "userId", userId,
            "role", newRole,
            "message", "Role updated successfully"
        );
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> updateUserStatus(
            @PathVariable String userId,
            @RequestBody Map<String, String> request) {
        String newStatus = request.get("status");
        // Placeholder implementation
        return Map.of(
            "userId", userId,
            "status", newStatus,
            "message", "Status updated successfully"
        );
    }
}
