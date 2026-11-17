package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchReviewRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> ids,
        @NotNull Action action,
        @Size(max = 2000) String comment) {
    public enum Action { APPROVE, REJECT }
}
