package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TokenCreateRequest(
        @NotBlank(message = "{validation.token.name.notBlank}")
        @Size(max = 64, message = "{validation.token.name.size}")
        String name,
        List<String> scopes
) {}
