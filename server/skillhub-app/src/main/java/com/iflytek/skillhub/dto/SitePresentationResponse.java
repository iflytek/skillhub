package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SitePresentationResponse(@NotNull SitePresentationSettings settings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {}
