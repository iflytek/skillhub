package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminAuditLogAppService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Read-only audit log endpoints for auditors and super administrators.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController extends BaseApiController {

    private final AdminAuditLogAppService adminAuditLogAppService;

    public AuditLogController(AdminAuditLogAppService adminAuditLogAppService,
                              ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminAuditLogAppService = adminAuditLogAppService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AuditLogItemResponse>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime) {
        return ok("response.success.read", adminAuditLogAppService.listAuditLogs(
                page,
                size,
                userId,
                action,
                requestId,
                ipAddress,
                resourceType,
                resourceId,
                startTime,
                endTime));
    }
}
