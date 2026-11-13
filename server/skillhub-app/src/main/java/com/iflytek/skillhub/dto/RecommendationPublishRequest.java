package com.iflytek.skillhub.dto;
import jakarta.validation.constraints.Min;
public record RecommendationPublishRequest(@Min(0) long version) {}
