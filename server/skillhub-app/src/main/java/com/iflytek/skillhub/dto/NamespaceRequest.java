package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NamespaceRequest(
        @NotBlank(message = "{validation.namespace.slug.notBlank}")
        @Size(min = 2, max = 64, message = "{validation.namespace.slug.size}")
        String slug,

        @NotBlank(message = "{validation.namespace.displayName.notBlank}")
        @Size(max = 128, message = "{validation.namespace.displayName.size}")
        String displayName,

        @Size(max = 512, message = "{validation.namespace.description.size}")
        String description
) {}
