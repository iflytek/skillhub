package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record LabelSortOrderItemRequest(
        @NotBlank String slug,
        int sortOrder
) {}
