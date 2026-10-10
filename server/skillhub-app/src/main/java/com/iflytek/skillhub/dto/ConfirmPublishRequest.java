package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to confirm publish for a PRIVATE skill version.
 */
public record ConfirmPublishRequest(
    @NotBlank(message = "Version is required")
    String version
) {}
