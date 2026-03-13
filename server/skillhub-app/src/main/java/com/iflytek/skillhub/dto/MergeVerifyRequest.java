package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MergeVerifyRequest(
    @NotNull(message = "合并请求 ID 不能为空")
    Long mergeRequestId,
    @NotBlank(message = "验证 token 不能为空")
    String verificationToken
) {}
