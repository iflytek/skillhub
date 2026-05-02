package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

import java.util.Set;

public record AuthMeResponse(
        String userId,
        String displayName,
        String email,
        String ussId,
        String avatarUrl,
        String oauthProvider,
        Set<String> platformRoles
) {
    public static AuthMeResponse from(PlatformPrincipal principal) {
        return new AuthMeResponse(
                principal.userId(),
                principal.displayName(),
                principal.email() != null ? principal.email() : "",
                null,
                principal.avatarUrl() != null ? principal.avatarUrl() : "",
                principal.oauthProvider(),
                principal.platformRoles()
        );
    }

    public static AuthMeResponse from(PlatformPrincipal principal, com.iflytek.skillhub.domain.user.UserAccount user) {
        return new AuthMeResponse(
                principal.userId(),
                principal.displayName(),
                principal.email() != null ? principal.email() : "",
                user.getUssId(),
                principal.avatarUrl() != null ? principal.avatarUrl() : "",
                principal.oauthProvider(),
                principal.platformRoles()
        );
    }
}
