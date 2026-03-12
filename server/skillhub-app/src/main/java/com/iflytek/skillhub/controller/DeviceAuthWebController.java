package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device")
public class DeviceAuthWebController extends BaseApiController {

    private final DeviceAuthService deviceAuthService;

    public DeviceAuthWebController(ApiResponseFactory responseFactory, DeviceAuthService deviceAuthService) {
        super(responseFactory);
        this.deviceAuthService = deviceAuthService;
    }

    @PostMapping("/authorize")
    public ApiResponse<MessageResponse> authorizeDevice(
        @RequestBody AuthorizeRequest request,
        @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        deviceAuthService.authorizeDeviceCode(request.userCode(), principal.userId());
        return ok("response.success.updated", new MessageResponse("Device authorized successfully"));
    }

    public record AuthorizeRequest(String userCode) {}
}
