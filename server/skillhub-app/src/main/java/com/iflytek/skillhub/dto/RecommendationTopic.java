package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RecommendationTopic(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{1,64}") String id,
        @NotBlank @Size(max = 80) String title,
        @NotNull @Size(max = 300) String description,
        @Schema(nullable = true) @Size(max = 256) String coverUrl,
        @Schema(nullable = true, allowableValues = {"office", "design", "learning", "content", "research", "security"})
        @Pattern(regexp = "office|design|learning|content|research|security") String coverStyle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
        @NotNull @Size(max = 24) List<@NotNull @Positive Long> skillIds) {
    public RecommendationTopic(String id, String title, String description, String coverUrl,
            boolean enabled, List<Long> skillIds) {
        this(id, title, description, coverUrl, null, enabled, skillIds);
    }
}
