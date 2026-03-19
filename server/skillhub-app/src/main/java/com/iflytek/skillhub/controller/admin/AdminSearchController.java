package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import com.iflytek.skillhub.search.SearchRebuildService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative maintenance endpoints for search-index operations reserved for super administrators.
 */
@RestController
@RequestMapping("/api/v1/admin/search")
public class AdminSearchController extends BaseApiController {

    private final SearchRebuildService searchRebuildService;
    private final AuditLogService auditLogService;

    public AdminSearchController(ApiResponseFactory responseFactory,
                                 SearchRebuildService searchRebuildService,
                                 AuditLogService auditLogService) {
        super(responseFactory);
        this.searchRebuildService = searchRebuildService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> rebuildAll(@AuthenticationPrincipal PlatformPrincipal principal,
                                        HttpServletRequest httpRequest) {
        searchRebuildService.rebuildAll();
        auditLogService.record(
                principal.userId(),
                "REBUILD_SEARCH_INDEX",
                "SEARCH_INDEX",
                null,
                MDC.get("requestId"),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                "{\"scope\":\"ALL\"}"
        );
        return ok("response.success.updated", null);
    }
}
