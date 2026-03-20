package com.iflytek.skillhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchLabelSortOrderRequest(
        @NotEmpty List<@Valid LabelSortOrderItemRequest> items
) {}
