package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for saving one draft file. Text is the default encoding; base64
 * transports binary-safe content.
 */
public record SaveDraftFileRequest(
        @NotBlank(message = "{error.badRequest}") String path,
        @NotNull(message = "{error.badRequest}") String content,
        String encoding,
        String contentType,
        Integer expectedRevision
) {

    public boolean isBase64() {
        return "base64".equalsIgnoreCase(encoding);
    }
}
