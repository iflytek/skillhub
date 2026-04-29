package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.uass.UassCallbackFlowService;
import com.iflytek.skillhub.auth.uass.UassLoginInitiationService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.UassLoginUrlResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for UASS browser-login initiation and callback completion.
 */
@RestController
@RequestMapping("/api/v1/auth/uass")
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
@ConditionalOnBean({UassLoginInitiationService.class, UassCallbackFlowService.class})
public class UassAuthController extends BaseApiController {

    private final UassLoginInitiationService uassLoginInitiationService;
    private final UassCallbackFlowService uassCallbackFlowService;

    public UassAuthController(ApiResponseFactory responseFactory,
                              UassLoginInitiationService uassLoginInitiationService,
                              UassCallbackFlowService uassCallbackFlowService) {
        super(responseFactory);
        this.uassLoginInitiationService = uassLoginInitiationService;
        this.uassCallbackFlowService = uassCallbackFlowService;
    }

    @GetMapping("/login-url")
    public ApiResponse<UassLoginUrlResponse> loginUrl(
            @RequestParam(name = "returnTo", required = false) String returnTo,
            HttpServletRequest request) {
        return ok(
                "response.success.read",
                new UassLoginUrlResponse(uassLoginInitiationService.buildLoginUrl(
                        returnTo,
                        URI.create(request.getRequestURL().toString())
                ))
        );
    }

    @GetMapping("/redirect")
    public ResponseEntity<Void> redirect(@RequestParam(name = "returnTo", required = false) String returnTo,
                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uassLoginInitiationService.buildLoginUrl(
                        returnTo,
                        URI.create(request.getRequestURL().toString())
                ))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(name = "code", required = false) String code,
                                         @RequestParam(name = "loginCode", required = false) String loginCode,
                                         @RequestParam("state") String state,
                                         HttpServletRequest request) {
        String redirectTo = uassCallbackFlowService.completeLogin(
                StringUtils.hasText(code) ? code : loginCode,
                state,
                URI.create(request.getRequestURL().toString()),
                request
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectTo)
                .build();
    }
}
