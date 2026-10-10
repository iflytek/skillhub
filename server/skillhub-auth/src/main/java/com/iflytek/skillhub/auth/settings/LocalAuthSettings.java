package com.iflytek.skillhub.auth.settings;

import java.time.Instant;

public record LocalAuthSettings(
        long id,
        boolean passwordLoginEnabled,
        boolean selfRegistrationEnabled,
        long version,
        Instant updatedAt
) {
    public boolean registrationAvailable() {
        return passwordLoginEnabled && selfRegistrationEnabled;
    }
}
