package com.iflytek.skillhub.dto;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
public record RecommendationTopicResponse(@NotNull String id, @NotNull String title, @NotNull String description,
        String coverUrl,
        @Schema(nullable = true, allowableValues = {"office", "design", "learning", "content", "research", "security"}) String coverStyle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int skillCount, @NotNull List<SkillSummaryResponse> skills) {
    public RecommendationTopicResponse(String id, String title, String description, String coverUrl,
            int skillCount, List<SkillSummaryResponse> skills) {
        this(id, title, description, coverUrl, null, skillCount, skills);
    }
}
