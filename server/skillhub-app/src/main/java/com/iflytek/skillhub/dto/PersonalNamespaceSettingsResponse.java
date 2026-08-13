package com.iflytek.skillhub.dto;

import java.util.List;

/**
 * @param supportedPlaceholders placeholder names the templates accept, so the console can document
 *                              them without hard-coding the list
 */
public record PersonalNamespaceSettingsResponse(
        boolean enabled,
        String slugTemplate,
        String displayNameTemplate,
        List<String> supportedPlaceholders
) {}
