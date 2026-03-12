package com.iflytek.skillhub.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'SUPER_ADMIN')")
    public Map<String, Object> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action) {
        // Placeholder implementation
        return Map.of(
            "logs", List.of(
                Map.of(
                    "id", "log-1",
                    "userId", "user-1",
                    "action", "CREATE_SKILL",
                    "resourceType", "SKILL",
                    "resourceId", "skill-123",
                    "timestamp", Instant.now().toString(),
                    "ipAddress", "192.168.1.1"
                ),
                Map.of(
                    "id", "log-2",
                    "userId", "user-2",
                    "action", "UPDATE_NAMESPACE",
                    "resourceType", "NAMESPACE",
                    "resourceId", "ns-456",
                    "timestamp", Instant.now().minusSeconds(3600).toString(),
                    "ipAddress", "192.168.1.2"
                )
            ),
            "total", 2,
            "page", page,
            "size", size
        );
    }
}
