package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LabelTranslationItemRequest(
        @NotBlank @Size(max = 16) String locale,
        @NotBlank @Size(max = 128) String displayName
) {}
