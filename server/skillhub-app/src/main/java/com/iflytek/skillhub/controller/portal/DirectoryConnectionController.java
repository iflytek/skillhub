package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.DirectoryConnectionResponse;
import com.iflytek.skillhub.service.DirectoryConnectionAppService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/directory-connections")
@PreAuthorize("isAuthenticated()")
public class DirectoryConnectionController extends BaseApiController {

    private final DirectoryConnectionAppService connections;

    public DirectoryConnectionController(
            DirectoryConnectionAppService connections,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.connections = connections;
    }

    @GetMapping
    public ApiResponse<List<DirectoryConnectionResponse>> list(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", connections.list(organizationId, principal.userId()));
    }

    @PostMapping
    public ApiResponse<DirectoryConnectionResponse> create(
            @PathVariable String organizationId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.created", connections.create(organizationId, principal.userId()));
    }

    @PostMapping("/{connectionId}/activate")
    public ApiResponse<DirectoryConnectionResponse> activate(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.activate(organizationId, connectionId, principal.userId()));
    }

    @PostMapping("/{connectionId}/suspend")
    public ApiResponse<DirectoryConnectionResponse> suspend(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.suspend(organizationId, connectionId, principal.userId()));
    }

    @PostMapping("/{connectionId}/disable")
    public ApiResponse<DirectoryConnectionResponse> disable(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.disable(organizationId, connectionId, principal.userId()));
    }

    @PostMapping("/{connectionId}/rotate-secret")
    public ApiResponse<DirectoryConnectionResponse> rotateSecret(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.updated",
                connections.rotateSecret(organizationId, connectionId, principal.userId()));
    }
}
