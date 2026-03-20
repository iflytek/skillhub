package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.PromotionActionRequest;
import com.iflytek.skillhub.dto.PromotionRequestDto;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.GovernanceWorkflowAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Promotion workflow endpoints that expose submission, review, and query
 * operations for cross-namespace promotion requests.
 */
@RestController
@RequestMapping({"/api/v1/promotions", "/api/web/promotions"})
public class PromotionController extends BaseApiController {

    private final GovernanceWorkflowAppService governanceWorkflowAppService;

    public PromotionController(GovernanceWorkflowAppService governanceWorkflowAppService,
                               ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.governanceWorkflowAppService = governanceWorkflowAppService;
    }

    @PostMapping
    public ApiResponse<PromotionResponseDto> submitPromotion(@RequestBody PromotionRequestDto request,
                                                             @RequestAttribute("userId") String userId,
                                                             @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                             HttpServletRequest httpRequest) {
        return ok(
                "response.success.created",
                governanceWorkflowAppService.submitPromotion(
                        request.sourceSkillId(),
                        request.sourceVersionId(),
                        request.targetNamespaceId(),
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest))
        );
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<PromotionResponseDto> approvePromotion(@PathVariable Long id,
                                                              @RequestBody(required = false) PromotionActionRequest request,
                                                              @RequestAttribute("userId") String userId,
                                                              HttpServletRequest httpRequest) {
        String comment = request != null ? request.comment() : null;
        return ok(
                "response.success.updated",
                governanceWorkflowAppService.approvePromotion(id, comment, userId, AuditRequestContext.from(httpRequest))
        );
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<PromotionResponseDto> rejectPromotion(@PathVariable Long id,
                                                             @RequestBody(required = false) PromotionActionRequest request,
                                                             @RequestAttribute("userId") String userId,
                                                             HttpServletRequest httpRequest) {
        String comment = request != null ? request.comment() : null;
        return ok(
                "response.success.updated",
                governanceWorkflowAppService.rejectPromotion(id, comment, userId, AuditRequestContext.from(httpRequest))
        );
    }

    @GetMapping
    public ApiResponse<PageResponse<PromotionResponseDto>> listPromotions(@RequestParam(defaultValue = "PENDING") String status,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size,
                                                                          @RequestAttribute("userId") String userId) {
        return ok("response.success.read", governanceWorkflowAppService.listPromotions(status, page, size, userId));
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<PromotionResponseDto>> listPendingPromotions(@RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "20") int size,
                                                                                 @RequestAttribute("userId") String userId) {
        return ok("response.success.read", governanceWorkflowAppService.listPendingPromotions(page, size, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionResponseDto> getPromotionDetail(@PathVariable Long id,
                                                                @RequestAttribute("userId") String userId) {
        return ok("response.success.read", governanceWorkflowAppService.getPromotionDetail(id, userId));
    }
}
