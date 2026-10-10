package com.iflytek.skillhub.dto;

public record LocalAuthCapabilitiesResponse(
        boolean passwordLoginEnabled,
        boolean selfRegistrationEnabled,
        boolean registrationAvailable
) {}
