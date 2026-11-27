package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.ConfirmedConnectionActionRequest;
import com.iflytek.skillhub.dto.LoginConnectionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionManagementResponse;
import com.iflytek.skillhub.dto.LoginConnectionResponse;
import com.iflytek.skillhub.dto.LoginConnectionRevisionCreateRequest;
import com.iflytek.skillhub.dto.LoginConnectionSecretRequest;
import com.iflytek.skillhub.dto.LoginConnectionTestResponse;
import com.iflytek.skillhub.service.EnterpriseConnectionAppService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/login-connections")
@PreAuthorize("isAuthenticated()")
public class EnterpriseConnectionController extends BaseApiController {
    private final EnterpriseConnectionAppService connections;

    public EnterpriseConnectionController(
            EnterpriseConnectionAppService connections, ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.connections = connections;
    }

    @GetMapping("/management")
    public ApiResponse<LoginConnectionManagementResponse> getLoginConnectionManagement(
            @PathVariable String organizationId, @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", connections.management(organizationId, principal.userId()));
    }

    @GetMapping("/{connectionId}")
    public ApiResponse<LoginConnectionResponse> getLoginConnection(
            @PathVariable String organizationId, @PathVariable String connectionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", connections.get(organizationId, connectionId, principal.userId()));
    }

    @PostMapping
    public ApiResponse<LoginConnectionResponse> createLoginConnection(
            @PathVariable String organizationId, @Valid @RequestBody LoginConnectionCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created", connections.create(organizationId, request, principal.userId()));
    }

    @PostMapping("/{connectionId}/revisions")
    public ApiResponse<LoginConnectionResponse> createLoginConnectionRevision(
            @PathVariable String organizationId, @PathVariable String connectionId,
            @Valid @RequestBody LoginConnectionRevisionCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created",
                connections.createRevision(organizationId, connectionId, request, principal.userId()));
    }

    @PostMapping("/{connectionId}/revisions/{revisionId}/secret")
    public ApiResponse<LoginConnectionResponse> writeLoginConnectionSecret(
            @PathVariable String organizationId, @PathVariable String connectionId, @PathVariable String revisionId,
            @Valid @RequestBody LoginConnectionSecretRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.writeSecret(organizationId, connectionId, revisionId, request, principal.userId()));
    }

    @PostMapping("/{connectionId}/revisions/{revisionId}/test")
    public ApiResponse<LoginConnectionTestResponse> testLoginConnectionRevision(
            @PathVariable String organizationId, @PathVariable String connectionId, @PathVariable String revisionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.testRevision(organizationId, connectionId, revisionId, principal.userId()));
    }

    @PostMapping("/{connectionId}/revisions/{revisionId}/activate")
    public ApiResponse<LoginConnectionResponse> activateLoginConnectionRevision(
            @PathVariable String organizationId, @PathVariable String connectionId, @PathVariable String revisionId,
            @Valid @RequestBody ConfirmedConnectionActionRequest confirmation,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.activate(organizationId, connectionId, revisionId, principal.userId()));
    }

    @PostMapping("/{connectionId}/suspend")
    public ApiResponse<LoginConnectionResponse> suspendLoginConnection(
            @PathVariable String organizationId, @PathVariable String connectionId,
            @Valid @RequestBody ConfirmedConnectionActionRequest confirmation,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", connections.suspend(organizationId, connectionId, principal.userId()));
    }

    @PostMapping("/{connectionId}/revisions/{revisionId}/resume")
    public ApiResponse<LoginConnectionResponse> resumeLoginConnection(
            @PathVariable String organizationId, @PathVariable String connectionId, @PathVariable String revisionId,
            @Valid @RequestBody ConfirmedConnectionActionRequest confirmation,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.resume(organizationId, connectionId, revisionId, principal.userId()));
    }

    @PostMapping("/{connectionId}/disable")
    public ApiResponse<LoginConnectionResponse> disableLoginConnection(
            @PathVariable String organizationId, @PathVariable String connectionId,
            @Valid @RequestBody ConfirmedConnectionActionRequest confirmation,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated", connections.disable(organizationId, connectionId, principal.userId()));
    }
}
