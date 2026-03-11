package com.iflytek.skillhub.auth.rbac;

import java.io.Serializable;
import java.util.Set;

public record PlatformPrincipal(
    Long userId,
    String displayName,
    String email,
    String avatarUrl,
    String oauthProvider,
    Set<String> platformRoles
) implements Serializable {}
