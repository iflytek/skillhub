package com.iflytek.skillhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SitePresentationUpdateRequest(@NotNull @Valid SitePresentationSettings settings,
                                            @NotNull @Min(0) Long version) {}
