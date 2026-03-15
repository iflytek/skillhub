package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.report.SkillReportService;
import com.iflytek.skillhub.dto.AdminSkillReportActionRequest;
import com.iflytek.skillhub.dto.AdminSkillReportSummaryResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillReportMutationResponse;
import com.iflytek.skillhub.service.AdminSkillReportAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/skill-reports")
public class AdminSkillReportController extends BaseApiController {

    private final AdminSkillReportAppService adminSkillReportAppService;
    private final SkillReportService skillReportService;

    public AdminSkillReportController(AdminSkillReportAppService adminSkillReportAppService,
                                      SkillReportService skillReportService,
                                      ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminSkillReportAppService = adminSkillReportAppService;
        this.skillReportService = skillReportService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AdminSkillReportSummaryResponse>> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success", adminSkillReportAppService.listReports(status, page, size));
    }

    @PostMapping("/{reportId}/resolve")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SkillReportMutationResponse> resolveReport(@PathVariable Long reportId,
                                                                  @RequestBody(required = false) AdminSkillReportActionRequest request,
                                                                  @AuthenticationPrincipal PlatformPrincipal principal,
                                                                  HttpServletRequest httpRequest) {
        var report = skillReportService.resolveReport(
                reportId,
                principal.userId(),
                request != null ? request.comment() : null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new SkillReportMutationResponse(report.getId(), report.getStatus().name()));
    }

    @PostMapping("/{reportId}/dismiss")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SkillReportMutationResponse> dismissReport(@PathVariable Long reportId,
                                                                  @RequestBody(required = false) AdminSkillReportActionRequest request,
                                                                  @AuthenticationPrincipal PlatformPrincipal principal,
                                                                  HttpServletRequest httpRequest) {
        var report = skillReportService.dismissReport(
                reportId,
                principal.userId(),
                request != null ? request.comment() : null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new SkillReportMutationResponse(report.getId(), report.getStatus().name()));
    }
}
