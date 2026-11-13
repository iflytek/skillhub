package com.iflytek.skillhub.dto;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
public record RecommendationAdminResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version, @NotNull RecommendationContent draft,
        @NotNull RecommendationContent published, @NotNull List<SkillSummaryResponse> skills,
        String publishedAt, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean usingDefaults) {}
