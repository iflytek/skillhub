package com.iflytek.skillhub.auth.device;

public record DeviceCodeResponse(
    String deviceCode,
    String userCode,
    String verificationUri,
    int expiresIn,
    int interval
) {}
