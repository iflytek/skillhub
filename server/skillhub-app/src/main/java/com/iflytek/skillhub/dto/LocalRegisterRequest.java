package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LocalRegisterRequest(
    @NotBlank(message = "用户名不能为空")
    String username,
    @NotBlank(message = "密码不能为空")
    String password,
    @Email(message = "邮箱格式不正确")
    String email
) {}
