package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public presentation fields only; section order and business destinations remain product-owned. */
public record SitePresentationSettings(
        @Schema(nullable = true) @Size(max = 80) @Pattern(regexp = "[^\\p{Cntrl}]*") String siteName,
        @Schema(nullable = true) @Size(max = 256) String logoUrl,
        @Schema(nullable = true) @Size(max = 256) String faviconUrl,
        @NotEmpty @Size(max = 9) Map<@Pattern(regexp = "hero|curated|capabilities|quickStart|popular|latest|enterprise|cta|footer") String,
                @NotNull @Valid SitePresentationSection> sections,
        @NotNull @Size(max = 9) Map<@Pattern(regexp = "repository|documentation|cliDocumentation|cliPackage|license|contributing|changelog|codeOfConduct|community") String,
                @NotNull @Size(max = 2048) String> links) {
    public static final List<String> SECTION_IDS = List.of(
            "hero", "curated", "capabilities", "quickStart", "popular", "latest", "enterprise", "cta", "footer");

    public static SitePresentationSettings defaults() {
        Map<String, SitePresentationSection> sections = new LinkedHashMap<>();
        for (String id : SECTION_IDS) sections.put(id, SitePresentationSection.defaults());
        return new SitePresentationSettings(null, null, null, Map.copyOf(sections), Map.of());
    }
}
