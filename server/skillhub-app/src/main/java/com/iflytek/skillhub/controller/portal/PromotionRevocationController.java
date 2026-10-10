package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PromotionRevocationActionRequest;
import com.iflytek.skillhub.dto.PromotionRevocationResponse;
import com.iflytek.skillhub.dto.PromotionRevocationSubmitRequest;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.PromotionRevocationPortalAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/promotion-revocations", "/api/web/promotion-revocations"})
public class PromotionRevocationController extends BaseApiController {
    private final PromotionRevocationPortalAppService appService;

    public PromotionRevocationController(PromotionRevocationPortalAppService appService,
                                         ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.appService = appService;
    }

    @PostMapping
    public ApiResponse<PromotionRevocationResponse> submit(@RequestBody PromotionRevocationSubmitRequest body,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            HttpServletRequest request) {
        return ok("response.success.created", appService.submit(body.sourceSkillId(), userId,
                roles, body.reason(), AuditRequestContext.from(request)));
    }

    @PostMapping("/source/{sourceSkillId}/direct")
    public ApiResponse<PromotionRevocationResponse> revokeDirect(@PathVariable Long sourceSkillId,
            @RequestBody(required = false) PromotionRevocationSubmitRequest body,
            @RequestAttribute("userId") String userId, HttpServletRequest request) {
        return ok("response.success.updated", appService.revokeDirect(sourceSkillId, userId,
                body != null ? body.reason() : null, AuditRequestContext.from(request)));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<PromotionRevocationResponse> approve(@PathVariable Long id,
            @RequestBody(required = false) PromotionRevocationActionRequest body,
            @RequestAttribute("userId") String userId, HttpServletRequest request) {
        return ok("response.success.updated", appService.approve(id, userId,
                body != null ? body.comment() : null, AuditRequestContext.from(request)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<PromotionRevocationResponse> reject(@PathVariable Long id,
            @RequestBody(required = false) PromotionRevocationActionRequest body,
            @RequestAttribute("userId") String userId, HttpServletRequest request) {
        return ok("response.success.updated", appService.reject(id, userId,
                body != null ? body.comment() : null, AuditRequestContext.from(request)));
    }

    @GetMapping("/pending")
    public ApiResponse<List<PromotionRevocationResponse>> pending(@RequestAttribute("userId") String userId) {
        return ok("response.success.read", appService.pending(userId));
    }

    @GetMapping("/history")
    public ApiResponse<PageResponse<PromotionRevocationResponse>> reviewedHistory(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read", appService.reviewedHistory(userId, page, size));
    }

    @GetMapping("/source/{sourceSkillId}/history")
    public ApiResponse<List<PromotionRevocationResponse>> history(@PathVariable Long sourceSkillId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        return ok("response.success.read", appService.history(sourceSkillId, userId, roles));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionRevocationResponse> get(@PathVariable Long id,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles) {
        return ok("response.success.read", appService.get(id, userId, roles));
    }
}
