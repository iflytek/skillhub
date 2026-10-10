package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

import java.util.Set;

public record CliWhoamiResponse(
        String userId,
        String displayName,
        String email,
        String avatarUrl,
        String authType,
        Set<String> platformRoles
) {
    public static CliWhoamiResponse from(PlatformPrincipal principal) {
        return new CliWhoamiResponse(
                principal.userId(),
                principal.displayName(),
                principal.email() != null ? principal.email() : "",
                principal.avatarUrl() != null ? principal.avatarUrl() : "",
                principal.oauthProvider(),
                principal.platformRoles()
        );
    }
}
