package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.domain.audit.AuditLogQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController extends BaseApiController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(ApiResponseFactory responseFactory,
                              AuditLogQueryService auditLogQueryService) {
        super(responseFactory);
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AuditLogItemResponse>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action) {
        var logs = auditLogQueryService.list(Math.max(0, page - 1), size, userId, action)
            .map(log -> new AuditLogItemResponse(
                String.valueOf(log.getId()),
                log.getActorUserId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId() != null ? String.valueOf(log.getTargetId()) : "",
                log.getCreatedAt(),
                log.getClientIp()
            ));
        return ok("response.success.read", PageResponse.from(logs));
    }
}
