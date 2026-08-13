package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonalNamespaceSettingsUpdateRequest(
        @NotNull Boolean enabled,
        @NotBlank @Size(max = 128) String slugTemplate,
        @NotBlank @Size(max = 128) String displayNameTemplate
) {}
