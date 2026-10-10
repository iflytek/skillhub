package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.device.DeviceCodeResponse;
import com.iflytek.skillhub.auth.device.DeviceTokenResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API endpoints for the CLI-style device authorization flow.
 */
@RestController
@RequestMapping("/api/v1/auth/device")
public class DeviceAuthController extends BaseApiController {

    private final DeviceAuthService deviceAuthService;

    public DeviceAuthController(ApiResponseFactory responseFactory, DeviceAuthService deviceAuthService) {
        super(responseFactory);
        this.deviceAuthService = deviceAuthService;
    }

    @PostMapping("/code")
    public ApiResponse<DeviceCodeResponse> requestDeviceCode() {
        return ok("response.success.created", deviceAuthService.generateDeviceCode());
    }

    @PostMapping("/token")
    public ApiResponse<DeviceTokenResponse> pollToken(@RequestBody TokenRequest request) {
        return ok("response.success.read", deviceAuthService.pollToken(request.deviceCode()));
    }

    public record TokenRequest(String deviceCode) {}
}
