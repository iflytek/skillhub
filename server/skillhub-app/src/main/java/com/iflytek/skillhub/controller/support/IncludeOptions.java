package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses optional response expansions from {@code include=...} query parameters.
 */
public final class IncludeOptions {

    private static final String LABELS = "labels";
    private static final Set<String> SUPPORTED = Set.of(LABELS);

    private IncludeOptions() {
    }

    public static boolean includesLabels(List<String> include) {
        if (include == null || include.isEmpty()) {
            return false;
        }

        boolean requested = false;
        for (String rawValue : include) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            for (String rawOption : rawValue.split(",")) {
                String option = rawOption.trim().toLowerCase(Locale.ROOT);
                if (option.isBlank()) {
                    continue;
                }
                if (!SUPPORTED.contains(option)) {
                    throw new DomainBadRequestException("error.request.include.unsupported", option);
                }
                requested = true;
            }
        }
        return requested;
    }
}
