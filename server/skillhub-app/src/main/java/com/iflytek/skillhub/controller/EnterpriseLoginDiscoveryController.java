package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryRequest;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryResponse;
import com.iflytek.skillhub.service.EnterpriseLoginDiscoveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class EnterpriseLoginDiscoveryController extends BaseApiController {
    private final EnterpriseLoginDiscoveryService discovery;

    public EnterpriseLoginDiscoveryController(
            ApiResponseFactory responseFactory,
            EnterpriseLoginDiscoveryService discovery
    ) {
        super(responseFactory);
        this.discovery = discovery;
    }

    @PostMapping("/enterprise/discovery")
    public ApiResponse<EnterpriseLoginDiscoveryResponse> discover(
            @Valid @RequestBody EnterpriseLoginDiscoveryRequest request
    ) {
        return ok("response.success.read", discovery.discover(request.identifier(), request.returnTo()));
    }
}
