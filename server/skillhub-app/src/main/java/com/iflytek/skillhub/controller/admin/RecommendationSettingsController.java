package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import com.iflytek.skillhub.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/system-config/recommendations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RecommendationSettingsController extends BaseApiController {
    private final RecommendationService service;
    public RecommendationSettingsController(RecommendationService service, ApiResponseFactory responses) { super(responses); this.service = service; }
    @GetMapping
    @Operation(operationId = "getRecommendationSettings", summary = "Read recommendation draft and published settings")
    public ApiResponse<RecommendationAdminResponse> current() { return ok("response.success.read", service.current()); }
    @PutMapping
    @Operation(operationId = "saveRecommendationDraft", summary = "Save the private recommendation draft")
    public ApiResponse<RecommendationAdminResponse> save(@Valid @RequestBody RecommendationUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal, HttpServletRequest http) {
        return ok("response.success.updated", service.save(request, principal.userId(), AuditRequestContext.from(http)));
    }
    @PostMapping("/publish")
    @Operation(operationId = "publishRecommendations", summary = "Publish the saved recommendation draft")
    public ApiResponse<RecommendationAdminResponse> publish(@Valid @RequestBody RecommendationPublishRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal, HttpServletRequest http) {
        return ok("response.success.updated", service.publish(request.version(), principal.userId(), AuditRequestContext.from(http)));
    }
    @GetMapping("/candidates")
    @Operation(operationId = "getRecommendationCandidates", summary = "Search eligible public skills for recommendations")
    public ApiResponse<PageResponse<SkillSummaryResponse>> candidates(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String label) {
        return ok("response.success.read", service.candidates(q, page, size, label));
    }
}
