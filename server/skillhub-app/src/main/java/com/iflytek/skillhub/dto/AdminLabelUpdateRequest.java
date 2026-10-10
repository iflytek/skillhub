package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.label.LabelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AdminLabelUpdateRequest(
        @NotNull LabelType type,
        boolean visibleInFilter,
        int sortOrder,
        @Valid @NotEmpty List<LabelTranslationItemRequest> translations
) {}
