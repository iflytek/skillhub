package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.AuthMeResponse;
import com.iflytek.skillhub.dto.AuthProviderResponse;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseApiController {

    private final OAuth2ClientProperties oAuth2ClientProperties;

    public AuthController(ApiResponseFactory responseFactory,
                          OAuth2ClientProperties oAuth2ClientProperties) {
        super(responseFactory);
        this.oAuth2ClientProperties = oAuth2ClientProperties;
    }

    @GetMapping("/me")
    public ApiResponse<AuthMeResponse> me(@AuthenticationPrincipal PlatformPrincipal principal,
                                          Authentication authentication) {
        if (principal == null || authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("error.auth.required");
        }
        return ok("response.success.read", AuthMeResponse.from(principal));
    }

    @GetMapping("/providers")
    public ApiResponse<List<AuthProviderResponse>> providers(
            @RequestParam(name = "returnTo", required = false) String returnTo) {
        String sanitizedReturnTo = com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        List<AuthProviderResponse> providers = new ArrayList<>(oAuth2ClientProperties.getRegistration().entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey()))
            .map(entry -> new AuthProviderResponse(
                entry.getKey(),
                entry.getValue().getClientName() != null && !entry.getValue().getClientName().isBlank()
                    ? entry.getValue().getClientName()
                    : entry.getKey(),
                buildAuthorizationUrl(entry.getKey(), sanitizedReturnTo)
            ))
            .toList());
        return ok("response.success.read", providers);
    }

    private String buildAuthorizationUrl(String registrationId, String returnTo) {
        String baseUrl = "/oauth2/authorization/" + registrationId;
        if (returnTo == null) {
            return baseUrl;
        }
        return baseUrl + "?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }
}
