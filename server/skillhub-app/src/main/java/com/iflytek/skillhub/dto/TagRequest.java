package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record TagRequest(
        @NotBlank String tagName,
        @NotBlank String targetVersion
) {}
