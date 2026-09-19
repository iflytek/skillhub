package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for creating a skill authoring draft. */
public record CreateDraftRequest(
        @NotBlank(message = "{error.badRequest}") String namespaceSlug,
        @NotBlank(message = "{error.badRequest}") String name,
        String requirement
) {
}
