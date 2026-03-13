package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record MergeInitiateRequest(
    @NotBlank(message = "待合并账号标识不能为空")
    String secondaryIdentifier
) {}
