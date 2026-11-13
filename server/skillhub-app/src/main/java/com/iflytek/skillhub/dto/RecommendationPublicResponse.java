package com.iflytek.skillhub.dto;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
public record RecommendationPublicResponse(@NotNull List<RecommendationTopicResponse> topics,
        @NotNull List<SkillSummaryResponse> featuredSkills) {}
