package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.uass.UassCallbackFlowService;
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
 * HTTP boundary for UASS callback completion. Redirect initiation and status
 * endpoints are introduced separately; this controller only exposes the
 * callback flow needed to finish browser session establishment.
 */
@RestController
@RequestMapping("/api/v1/auth/uass")
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
@ConditionalOnBean(UassCallbackFlowService.class)
public class UassAuthController {

    private final UassCallbackFlowService uassCallbackFlowService;

    public UassAuthController(UassCallbackFlowService uassCallbackFlowService) {
        this.uassCallbackFlowService = uassCallbackFlowService;
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
