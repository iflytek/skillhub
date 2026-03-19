package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness endpoint used by tests, probes, and basic uptime checks.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController extends BaseApiController {

    public HealthController(ApiResponseFactory responseFactory) {
        super(responseFactory);
    }

    @GetMapping("/health")
    public ApiResponse<MessageResponse> health() {
        return ok("response.success.health", new MessageResponse("UP"));
    }
}
