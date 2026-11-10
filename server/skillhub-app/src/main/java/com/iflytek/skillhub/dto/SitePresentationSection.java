package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Overrides for one existing section; absent text and item keys retain product defaults. */
public record SitePresentationSection(
        @NotNull Boolean enabled,
        @NotNull @Size(max = 20) Map<@NotBlank String,
                @NotNull @Size(max = 4) Map<@Pattern(regexp = "en|zh|ko|ru") String,
                        @NotNull @Size(max = 1000) String>> texts,
        @Schema(nullable = true) @Size(max = 6) Map<@NotBlank String, @NotNull Boolean> items) {
    public static SitePresentationSection defaults() {
        return new SitePresentationSection(true, Map.of(), Map.of());
    }
}
