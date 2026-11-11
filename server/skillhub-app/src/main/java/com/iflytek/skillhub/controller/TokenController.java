package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenScopeService;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.auth.token.ApiTokenScopes;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.TokenCreateRequest;
import com.iflytek.skillhub.dto.TokenCreateResponse;
import com.iflytek.skillhub.dto.TokenExpirationUpdateRequest;
import com.iflytek.skillhub.dto.TokenSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Self-service API token management endpoints for authenticated users.
 */
@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController extends BaseApiController {

    private final ApiTokenService apiTokenService;
    private final ObjectMapper objectMapper;
    private final ApiTokenScopeService scopeService;

    public TokenController(ApiTokenService apiTokenService, ApiResponseFactory responseFactory,
                           ObjectMapper objectMapper, ApiTokenScopeService scopeService) {
        super(responseFactory);
        this.apiTokenService = apiTokenService;
        this.objectMapper = objectMapper;
        this.scopeService = scopeService;
    }

    @PostMapping
    public ApiResponse<TokenCreateResponse> create(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @Valid @RequestBody TokenCreateRequest request) {
        String scopeJson;
        if (request.scopes() == null) {
            scopeJson = ApiTokenScopes.DEFAULT_USER_SCOPE_JSON;
        } else {
            try {
                scopeJson = objectMapper.writeValueAsString(request.scopes());
            } catch (JsonProcessingException e) {
                throw new DomainBadRequestException("error.apiToken.scope.unknown", "invalid scope list");
            }
        }

        var result = principal.tokenCapabilities() == null
                ? apiTokenService.rotateToken(principal.userId(), request.name(), scopeJson, request.expiresAt())
                : apiTokenService.rotateToken(principal.userId(), request.name(), scopeJson, request.expiresAt(), principal);
        return ok("response.success.created", new TokenCreateResponse(
                result.rawToken(),
                result.entity().getId(),
                result.entity().getName(),
                result.entity().getTokenPrefix(),
                formatInstant(result.entity().getCreatedAt()),
                formatInstant(result.entity().getExpiresAt()),
                capabilities(result.entity()),
                legacyCompat(result.entity()),
                request.scopes() == null
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
            formatInstant(t.getCreatedAt()),
            formatInstant(t.getExpiresAt()),
            formatInstant(t.getLastUsedAt()),
            capabilities(t),
            legacyCompat(t)
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

    @PutMapping("/{id}/expiration")
    public ApiResponse<TokenSummaryResponse> updateExpiration(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @PathVariable Long id,
            @RequestBody TokenExpirationUpdateRequest request) {
        var token = principal.tokenCapabilities() == null
                ? apiTokenService.updateExpiration(id, principal.userId(), request.expiresAt())
                : apiTokenService.updateExpiration(id, principal.userId(), request.expiresAt(), principal);
        return ok("response.success.updated", new TokenSummaryResponse(
                token.getId(),
                token.getName(),
                token.getTokenPrefix(),
                formatInstant(token.getCreatedAt()),
                formatInstant(token.getExpiresAt()),
                formatInstant(token.getLastUsedAt()),
                capabilities(token),
                legacyCompat(token)
        ));
    }

    private List<String> capabilities(ApiToken token) {
        return scopeService.effectiveCapabilities(token.getScopeJson()).stream().sorted().toList();
    }

    private boolean legacyCompat(ApiToken token) {
        return scopeService.parseScopes(token.getScopeJson()).contains(ApiTokenScopes.LEGACY_PUBLISH);
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : value.toString();
    }
}
