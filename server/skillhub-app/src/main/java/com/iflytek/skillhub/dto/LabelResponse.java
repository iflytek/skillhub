package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.label.LabelType;

public record LabelResponse(
        String slug,
        LabelType type,
        String displayName
) {}
