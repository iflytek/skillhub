package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record SystemAuthSettingsUpdateRequest(
        @NotNull Boolean passwordLoginEnabled,
        @NotNull Boolean selfRegistrationEnabled,
        @NotNull Long version
) {}
