package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auditor-specific endpoints for read-only access to platform data.
 * AUDITOR role can view all data for compliance and auditing purposes.
 */
@RestController
@RequestMapping("/api/v1/admin/auditor")
public class AdminAuditorController extends BaseApiController {

    public AdminAuditorController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    // TODO: Implement auditor-specific endpoints
    // Examples:
    // - GET /all-skills - View all skills including hidden
    // - GET /review-history - View review history
    // - GET /user-activity - View user activity logs
    // - GET /statistics - View platform statistics

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('AUDITOR', 'SUPER_ADMIN')")
    public ApiResponse<String> getStatus() {
        return ok("response.success", "Auditor API is ready. More endpoints coming soon.");
    }
}
