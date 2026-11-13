package com.iflytek.skillhub.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record RecommendationUpdateRequest(@Min(0) long version, @NotNull @Valid RecommendationContent draft) {}
