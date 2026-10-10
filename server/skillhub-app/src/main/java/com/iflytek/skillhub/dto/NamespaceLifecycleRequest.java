package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Size;

public record NamespaceLifecycleRequest(
        @Size(max = 512, message = "{validation.namespace.description.size}")
        String reason
) {}
