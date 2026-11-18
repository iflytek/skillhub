package com.iflytek.skillhub.dto;

import java.util.List;
import jakarta.validation.constraints.NotNull;

public record BatchReviewResponse(@NotNull List<BatchReviewItem> items) {
    public record BatchReviewItem(@NotNull Long id, @NotNull String outcome, @NotNull String reason, @NotNull String message) {}
}
