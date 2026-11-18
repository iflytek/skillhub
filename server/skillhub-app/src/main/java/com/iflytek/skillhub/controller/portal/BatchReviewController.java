package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.BatchReviewAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/reviews", "/api/web/reviews"})
public class BatchReviewController extends BaseApiController {
    private final BatchReviewAppService service;

    public BatchReviewController(BatchReviewAppService service, ApiResponseFactory responses) {
        super(responses);
        this.service = service;
    }

    @PostMapping("/batch-preview")
    public ApiResponse<BatchReviewResponse> previewBatchReviews(@Valid @RequestBody BatchReviewRequest request,
            @RequestAttribute("userId") String actor, HttpServletRequest httpRequest) {
        return ok("response.success.read", service.process(request, actor, AuditRequestContext.from(httpRequest), true));
    }

    @PostMapping("/batch-decide")
    public ApiResponse<BatchReviewResponse> decideBatchReviews(@Valid @RequestBody BatchReviewRequest request,
            @RequestAttribute("userId") String actor, HttpServletRequest httpRequest) {
        return ok("response.success.updated", service.process(request, actor, AuditRequestContext.from(httpRequest), false));
    }
}
