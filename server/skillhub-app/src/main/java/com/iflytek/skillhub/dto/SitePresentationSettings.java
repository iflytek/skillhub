package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Public presentation fields only; never an unfiltered projection of system_setting. */
public record SitePresentationSettings(
        @Schema(nullable = true) @Size(max = 80) @Pattern(regexp = "[^\\p{Cntrl}]*") String siteName,
        @Schema(nullable = true) @Size(max = 256) String logoUrl,
        @Schema(nullable = true) @Size(max = 256) String faviconUrl,
        @NotNull HomepageMode homepageMode,
        @NotNull @Size(max = 4) Map<@Pattern(regexp = "en|zh|ko|ru") String, @Size(max = 160) String> heroTitle,
        @NotNull @Size(max = 4) Map<@Pattern(regexp = "en|zh|ko|ru") String, @Size(max = 1000) String> heroDescription,
        @NotNull Boolean showPopular,
        @NotNull Boolean showLatest,
        @NotNull Boolean showQuickStart,
        @NotNull Boolean footerVisible) {
    public enum HomepageMode { LANDING, CATALOG }

    public static SitePresentationSettings defaults() {
        return new SitePresentationSettings(null, null, null, HomepageMode.LANDING,
                Map.of(), Map.of(), true, true, true, true);
    }
}
