package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryRequest;
import com.iflytek.skillhub.dto.EnterpriseLoginDiscoveryResponse;
import com.iflytek.skillhub.service.EnterpriseLoginDiscoveryService;
import com.iflytek.skillhub.service.EnterpriseLoginStartService;
import com.iflytek.skillhub.service.EnterpriseLoginCallbackService;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class EnterpriseLoginDiscoveryController extends BaseApiController {
    private final EnterpriseLoginDiscoveryService discovery;
    private final EnterpriseLoginStartService start;
    private final EnterpriseLoginCallbackService callback;

    public EnterpriseLoginDiscoveryController(
            ApiResponseFactory responseFactory,
            EnterpriseLoginDiscoveryService discovery,
            EnterpriseLoginStartService start,
            EnterpriseLoginCallbackService callback
    ) {
        super(responseFactory);
        this.discovery = discovery;
        this.start = start;
        this.callback = callback;
    }

    @PostMapping("/enterprise/discovery")
    public ApiResponse<EnterpriseLoginDiscoveryResponse> discover(
            @Valid @RequestBody EnterpriseLoginDiscoveryRequest request
    ) {
        return ok("response.success.read", discovery.discover(request.identifier(), request.returnTo()));
    }

    @GetMapping("/enterprise/{publicHandle}/start")
    public ResponseEntity<Void> start(
            @PathVariable String publicHandle,
            @RequestParam(required = false) String returnTo
    ) {
        try {
            var result = start.start(publicHandle, returnTo);
            ResponseCookie cookie = ResponseCookie.from("SKILLHUB_ENT_BINDING", result.browserBinding())
                    .path("/api/v1/auth/enterprise/" + publicHandle)
                    .httpOnly(true).secure(true).sameSite("Lax").maxAge(Duration.ofMinutes(5)).build();
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, result.authorizationUri().toString())
                    .header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
        } catch (RuntimeException rejected) {
            return redirect("/login?enterpriseError=unavailable", "/api/v1/auth/enterprise/" + publicHandle);
        }
    }

    @GetMapping("/enterprise/{publicHandle}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String publicHandle,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @CookieValue(name = "SKILLHUB_ENT_BINDING", required = false) String binding,
            HttpServletRequest request
    ) {
        String clearPath = "/api/v1/auth/enterprise/" + publicHandle;
        try {
            var result = callback.complete(publicHandle, state, code, binding, request);
            return redirect(result.returnTo(), clearPath);
        } catch (RuntimeException rejected) {
            return redirect("/login?enterpriseError=unavailable", clearPath);
        }
    }

    private static ResponseEntity<Void> redirect(String target, String cookiePath) {
        ResponseCookie clearCookie = ResponseCookie.from("SKILLHUB_ENT_BINDING", "")
                .path(cookiePath).httpOnly(true).secure(true).sameSite("Lax").maxAge(Duration.ZERO).build();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target)
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString()).build();
    }
}
