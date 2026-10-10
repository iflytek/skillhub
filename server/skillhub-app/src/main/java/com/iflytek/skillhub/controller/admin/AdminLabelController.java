package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.AdminLabelCreateRequest;
import com.iflytek.skillhub.dto.AdminLabelUpdateRequest;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.LabelDefinitionResponse;
import com.iflytek.skillhub.dto.LabelSortOrderUpdateRequest;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.LabelAdminAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/labels")
public class AdminLabelController extends BaseApiController {

    private final LabelAdminAppService labelAdminAppService;

    public AdminLabelController(LabelAdminAppService labelAdminAppService,
                                ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.labelAdminAppService = labelAdminAppService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<LabelDefinitionResponse>> listLabels() {
        return ok("response.success.read", labelAdminAppService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<LabelDefinitionResponse> createLabel(@Valid @RequestBody AdminLabelCreateRequest request,
                                                            @AuthenticationPrincipal PlatformPrincipal principal,
                                                            HttpServletRequest httpRequest) {
        return ok("response.success.created",
                labelAdminAppService.create(request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<LabelDefinitionResponse> updateLabel(@PathVariable String slug,
                                                            @Valid @RequestBody AdminLabelUpdateRequest request,
                                                            @AuthenticationPrincipal PlatformPrincipal principal,
                                                            HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                labelAdminAppService.update(slug, request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MessageResponse> deleteLabel(@PathVariable String slug,
                                                    @AuthenticationPrincipal PlatformPrincipal principal,
                                                    HttpServletRequest httpRequest) {
        labelAdminAppService.delete(slug, principal.userId(), AuditRequestContext.from(httpRequest));
        return ok("response.success.deleted", new MessageResponse("Label deleted"));
    }

    @PutMapping("/sort-order")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<LabelDefinitionResponse>> updateSortOrder(@Valid @RequestBody LabelSortOrderUpdateRequest request,
                                                                      @AuthenticationPrincipal PlatformPrincipal principal,
                                                                      HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                labelAdminAppService.updateSortOrder(request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }
}
