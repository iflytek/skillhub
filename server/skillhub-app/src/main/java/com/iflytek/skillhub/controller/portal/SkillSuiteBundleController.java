package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillSuiteBundleConfirmRequest;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationResponse;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationDetailResponse;
import com.iflytek.skillhub.dto.SkillSuiteBundlePreviewResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleConfirmationAppService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundlePreviewAppService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleOperationQueryService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleOperationCommandService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Transport-only endpoints for the two-stage Suite Bundle import workflow. */
@RestController
@Tag(name = "Skill Suite Bundles")
@RequestMapping({"/api/v1/suite-bundles", "/api/web/suite-bundles"})
public class SkillSuiteBundleController extends BaseApiController {

    private final SkillSuiteBundlePreviewAppService previewService;
    private final SkillSuiteBundleConfirmationAppService confirmationService;
    private final SkillSuiteBundleOperationQueryService operationQueryService;
    private final SkillSuiteBundleOperationCommandService operationCommandService;
    private final SkillSuiteBundleResponseMapper responseMapper;

    public SkillSuiteBundleController(
            SkillSuiteBundlePreviewAppService previewService,
            SkillSuiteBundleConfirmationAppService confirmationService,
            SkillSuiteBundleOperationQueryService operationQueryService,
            SkillSuiteBundleOperationCommandService operationCommandService,
            SkillSuiteBundleResponseMapper responseMapper,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.previewService = previewService;
        this.confirmationService = confirmationService;
        this.operationQueryService = operationQueryService;
        this.operationCommandService = operationCommandService;
        this.responseMapper = responseMapper;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            operationId = "previewSkillSuiteBundle",
            summary = "Validate and preview one Suite Bundle archive",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)
    )
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<SkillSuiteBundlePreviewResponse> preview(
            @RequestPart("file") MultipartFile file,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) throws IOException {
        return ok("response.success.read", responseMapper.toResponse(previewService.preview(
                file, userId, roles == null ? Map.of() : roles, platformRoles(principal))));
    }

    @PostMapping("/previews/{previewToken}/confirm")
    @Operation(operationId = "confirmSkillSuiteBundle", summary = "Confirm one exact Suite Bundle preview")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<SkillSuiteBundleOperationResponse> confirm(
            @PathVariable String previewToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String clientRequestId,
            @Valid @RequestBody SkillSuiteBundleConfirmRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created", responseMapper.toResponse(confirmationService.confirm(
                previewToken, clientRequestId, request.warningDigest(), userId,
                roles == null ? Map.of() : roles, platformRoles(principal))));
    }

    @GetMapping("/operations/{operationId}")
    @Operation(operationId = "getSkillSuiteBundleOperation", summary = "Get one authorized Suite Bundle operation")
    public ApiResponse<SkillSuiteBundleOperationDetailResponse> getOperation(
            @PathVariable String operationId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", operationQueryService.get(
                operationId, userId, roles == null ? Map.of() : roles, platformRoles(principal)));
    }

    @PostMapping("/operations/{operationId}/cancel")
    @Operation(operationId = "cancelSkillSuiteBundleOperation", summary = "Cancel one active Suite Bundle operation")
    public ApiResponse<SkillSuiteBundleOperationResponse> cancelOperation(
            @PathVariable String operationId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", operationCommandService.cancel(
                operationId, userId, roles == null ? Map.of() : roles, platformRoles(principal)));
    }

    @PostMapping("/operations/{operationId}/retry")
    @Operation(operationId = "retrySkillSuiteBundleOperation", summary = "Retry one blocked Suite Bundle operation")
    public ApiResponse<SkillSuiteBundleOperationResponse> retryOperation(
            @PathVariable String operationId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", operationCommandService.retry(
                operationId, userId, roles == null ? Map.of() : roles, platformRoles(principal)));
    }

    private Set<String> platformRoles(PlatformPrincipal principal) {
        return principal == null || principal.platformRoles() == null
                ? Set.of()
                : principal.platformRoles();
    }
}
