package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController {

    private final ApiTokenService apiTokenService;

    public TokenController(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String scopeJson = body.containsKey("scopes")
            ? body.get("scopes").toString() : "[\"skill:read\",\"skill:publish\"]";

        var result = apiTokenService.createToken(principal.userId(), name, scopeJson);
        return ResponseEntity.ok(Map.of(
            "token", result.rawToken(),
            "id", result.entity().getId(),
            "name", result.entity().getName(),
            "tokenPrefix", result.entity().getTokenPrefix()
        ));
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal PlatformPrincipal principal) {
        var tokens = apiTokenService.listActiveTokens(principal.userId());
        var result = tokens.stream().map(t -> Map.of(
            "id", t.getId(),
            "name", t.getName(),
            "tokenPrefix", t.getTokenPrefix(),
            "createdAt", t.getCreatedAt().toString(),
            "expiresAt", t.getExpiresAt() != null ? t.getExpiresAt().toString() : "",
            "lastUsedAt", t.getLastUsedAt() != null ? t.getLastUsedAt().toString() : ""
        )).toList();
        return ResponseEntity.ok(Map.of("data", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal PlatformPrincipal principal,
            @PathVariable Long id) {
        apiTokenService.revokeToken(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
