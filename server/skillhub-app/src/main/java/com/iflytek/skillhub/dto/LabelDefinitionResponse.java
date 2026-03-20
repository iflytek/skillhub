package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record LabelDefinitionResponse(
        String slug,
        String type,
        boolean visibleInFilter,
        int sortOrder,
        List<LabelTranslationResponse> translations,
        Instant createdAt
) {}
