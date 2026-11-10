package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record SitePresentationResponse(@NotNull SitePresentationSettings settings, long version) {}
