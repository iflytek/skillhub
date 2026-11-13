package com.iflytek.skillhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record RecommendationContent(
        @NotNull @Size(max = 12) List<@NotNull @Valid RecommendationTopic> topics,
        @NotNull @Size(max = 24) List<@NotNull @Positive Long> featuredSkillIds) {
    public static RecommendationContent empty() { return new RecommendationContent(List.of(), List.of()); }
}
