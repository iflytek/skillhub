package com.iflytek.skillhub.auth.oauth;

import java.util.Map;

public record OAuthClaims(
    String provider,
    String subject,
    String email,
    boolean emailVerified,
    String providerLogin,
    Map<String, Object> extra
) {}
