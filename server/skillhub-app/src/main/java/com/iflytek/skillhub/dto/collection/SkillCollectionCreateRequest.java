package com.iflytek.skillhub.dto.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SkillCollectionCreateRequest(
        @NotBlank(message = "{validation.skillCollection.title.notBlank}")
        @Size(max = 200, message = "{validation.skillCollection.title.size}")
        String title,

        @Size(max = 2000, message = "{validation.skillCollection.description.size}")
        String description,

        @NotBlank(message = "{validation.skillCollection.visibility.notBlank}")
        @Pattern(regexp = "PUBLIC|PRIVATE", message = "{validation.skillCollection.visibility.allowed}")
        String visibility,

        @NotBlank(message = "{validation.skillCollection.slug.notBlank}")
        @Size(min = 2, max = 64, message = "{validation.skillCollection.slug.size}")
        @Pattern(
                regexp = "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$",
                message = "{validation.skillCollection.slug.pattern}"
        )
        String slug
) {}
