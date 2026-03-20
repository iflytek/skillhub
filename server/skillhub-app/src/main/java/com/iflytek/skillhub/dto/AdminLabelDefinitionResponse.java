package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.label.LabelType;
import java.time.Instant;
import java.util.List;

public record AdminLabelDefinitionResponse(
        String slug,
        LabelType type,
        boolean visibleInFilter,
        int sortOrder,
        List<LabelTranslationResponse> translations,
        Instant createdAt
) {}
