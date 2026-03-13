package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record DirectLoginRequest(
    @NotBlank(message = "认证提供方不能为空")
    String provider,
    @NotBlank(message = "用户名不能为空")
    String username,
    @NotBlank(message = "密码不能为空")
    String password
) {}
