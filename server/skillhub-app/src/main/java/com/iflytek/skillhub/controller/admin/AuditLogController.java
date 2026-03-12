package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController extends BaseApiController {

    public AuditLogController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AuditLogItemResponse>> listAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action) {
        List<AuditLogItemResponse> logs = List.of(
                new AuditLogItemResponse(
                        "log-1", "user-1", "CREATE_SKILL", "SKILL", "skill-123", Instant.now(), "192.168.1.1"
                ),
                new AuditLogItemResponse(
                        "log-2", "user-2", "UPDATE_NAMESPACE", "NAMESPACE", "ns-456",
                        Instant.now().minusSeconds(3600), "192.168.1.2"
                )
        );
        return ok("response.success.read", PageResponse.from(new PageImpl<>(logs)));
    }
}
