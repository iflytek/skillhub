package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "当前密码不能为空")
    String currentPassword,
    @NotBlank(message = "新密码不能为空")
    String newPassword
) {}
