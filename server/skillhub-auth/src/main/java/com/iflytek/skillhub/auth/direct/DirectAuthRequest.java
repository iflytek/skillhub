package com.iflytek.skillhub.auth.direct;

public record DirectAuthRequest(
    String username,
    String password
) {}
