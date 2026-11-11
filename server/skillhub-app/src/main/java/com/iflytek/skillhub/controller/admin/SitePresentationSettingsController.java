package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SitePresentationAssetResponse;
import com.iflytek.skillhub.dto.SitePresentationResponse;
import com.iflytek.skillhub.dto.SitePresentationUpdateRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SitePresentationAssetService;
import com.iflytek.skillhub.service.SitePresentationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/system-config/presentation")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SitePresentationSettingsController extends BaseApiController {
    private final SitePresentationService service;
    private final SitePresentationAssetService assets;

    public SitePresentationSettingsController(SitePresentationService service, SitePresentationAssetService assets,
                                              ApiResponseFactory responses) {
        super(responses);
        this.service = service;
        this.assets = assets;
    }

    @GetMapping
    @Operation(summary = "Read managed site presentation settings")
    public ApiResponse<SitePresentationResponse> current() { return ok("response.success.read", service.current()); }

    @PutMapping
    @Operation(summary = "Update site presentation settings")
    public ApiResponse<SitePresentationResponse> update(@Valid @RequestBody SitePresentationUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal, HttpServletRequest httpRequest) {
        return ok("response.success.updated", service.update(request, principal.userId(),
                AuditRequestContext.from(httpRequest)));
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a public branding image (PNG or JPEG, up to 2 MiB and 1024 pixels per side)")
    public ApiResponse<SitePresentationAssetResponse> upload(@RequestParam("file") MultipartFile file) {
        return ok("response.success.updated", assets.upload(file));
    }
}
