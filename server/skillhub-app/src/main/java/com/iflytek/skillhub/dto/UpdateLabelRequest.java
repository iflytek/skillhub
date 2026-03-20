package com.iflytek.skillhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateLabelRequest(
        @NotBlank String type,
        @NotNull Boolean visibleInFilter,
        int sortOrder,
        @NotEmpty List<@Valid LabelTranslationInput> translations
) {}
