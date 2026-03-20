package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record AdminLabelResponse(
        String slug,
        String type,
        boolean visibleInFilter,
        int sortOrder,
        List<LabelTranslationResponse> translations,
        Instant createdAt
) {}
