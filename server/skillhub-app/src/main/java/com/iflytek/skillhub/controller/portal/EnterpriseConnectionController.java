package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.ConfirmedConnectionActionRequest;
import com.iflytek.skillhub.dto.LoginConnectionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionResponse;
import com.iflytek.skillhub.dto.LoginConnectionRevisionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionTestResponse;
import com.iflytek.skillhub.service.EnterpriseConnectionAppService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transport-only Organization API for the Login Connection control plane. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/login-connections")
public class EnterpriseConnectionController extends BaseApiController {

    private final EnterpriseConnectionAppService appService;

    public EnterpriseConnectionController(
            EnterpriseConnectionAppService appService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.appService = appService;
    }

    @GetMapping
    public ApiResponse<List<LoginConnectionResponse>> list(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", appService.list(organizationId, principal.userId()));
    }

    @GetMapping("/{connectionId}")
    public ApiResponse<LoginConnectionResponse> get(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.read",
                appService.get(organizationId, connectionId, principal.userId())
        );
    }

    @PostMapping
    public ApiResponse<LoginConnectionResponse> create(
            @PathVariable String organizationId,
            @Valid @RequestBody LoginConnectionCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.created",
                appService.create(organizationId, request, principal.userId())
        );
    }

    @PostMapping("/{connectionId}/revisions")
    public ApiResponse<LoginConnectionResponse> createRevision(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @Valid @RequestBody LoginConnectionRevisionCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.created",
                appService.createRevision(
                        organizationId,
                        connectionId,
                        request,
                        principal.userId()
                )
        );
    }

    @PostMapping("/{connectionId}/revisions/{revisionId}/test")
    public ApiResponse<LoginConnectionTestResponse> testRevision(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @PathVariable String revisionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.testRevision(
                        organizationId,
                        connectionId,
                        revisionId,
                        principal.userId()
                )
        );
    }

    @PostMapping("/{connectionId}/revisions/{revisionId}/activate")
    public ApiResponse<LoginConnectionResponse> activate(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @PathVariable String revisionId,
            @Valid @RequestBody ConfirmedConnectionActionRequest ignoredConfirmation,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.activate(
                        organizationId,
                        connectionId,
                        revisionId,
                        principal.userId()
                )
        );
    }

    @PostMapping("/{connectionId}/suspend")
    public ApiResponse<LoginConnectionResponse> suspend(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @Valid @RequestBody ConfirmedConnectionActionRequest ignoredConfirmation,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.suspend(organizationId, connectionId, principal.userId())
        );
    }

    @PostMapping("/{connectionId}/disable")
    public ApiResponse<LoginConnectionResponse> disable(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @Valid @RequestBody ConfirmedConnectionActionRequest ignoredConfirmation,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok(
                "response.success.updated",
                appService.disable(organizationId, connectionId, principal.userId())
        );
    }
}
