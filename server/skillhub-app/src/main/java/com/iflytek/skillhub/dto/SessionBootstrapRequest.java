package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record SessionBootstrapRequest(
    @NotBlank(message = "认证提供方不能为空")
    String provider
) {}
