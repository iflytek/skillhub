package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SystemAuthSettingsResponse(
        boolean passwordLoginEnabled,
        boolean selfRegistrationEnabled,
        long version,
        Instant updatedAt
) {}
