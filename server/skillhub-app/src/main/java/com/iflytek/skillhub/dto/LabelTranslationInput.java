package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record LabelTranslationInput(
        @NotBlank String locale,
        @NotBlank String displayName
) {}
