package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record MergeVerifyRequest(
    @NotNull(message = "合并请求 ID 不能为空")
    Long mergeRequestId
) {}
