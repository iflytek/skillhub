package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.TokenCreateRequest;
import com.iflytek.skillhub.dto.TokenCreateResponse;
import com.iflytek.skillhub.dto.TokenSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController extends BaseApiController {

    private final ApiTokenService apiTokenService;

    public TokenController(ApiTokenService apiTokenService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.apiTokenService = apiTokenService;
    }

    @PostMapping
    public ApiResponse<TokenCreateResponse> create(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @Valid @RequestBody TokenCreateRequest request) {
        String scopeJson = request.scopes() == null || request.scopes().isEmpty()
                ? "[\"skill:read\",\"skill:publish\"]"
                : request.scopes().toString();

        var result = apiTokenService.createToken(principal.userId(), request.name(), scopeJson);
        return ok("response.success.created", new TokenCreateResponse(
                result.rawToken(),
                result.entity().getId(),
                result.entity().getName(),
                result.entity().getTokenPrefix(),
                result.entity().getCreatedAt().toString(),
                result.entity().getExpiresAt() != null ? result.entity().getExpiresAt().toString() : ""
        ));
    }

    @GetMapping
    public ApiResponse<PageResponse<TokenSummaryResponse>> list(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var tokens = apiTokenService.listActiveTokens(principal.userId(), page, size);
        var result = tokens.map(t -> new TokenSummaryResponse(
            t.getId(),
            t.getName(),
            t.getTokenPrefix(),
            t.getCreatedAt().toString(),
            t.getExpiresAt() != null ? t.getExpiresAt().toString() : "",
            t.getLastUsedAt() != null ? t.getLastUsedAt().toString() : ""
        ));
        return ok("response.success.read", PageResponse.from(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @PathVariable Long id) {
        apiTokenService.revokeToken(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
