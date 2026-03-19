package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;

/**
 * Minimal controller base class that centralizes access to the standard API response factory.
 */
public abstract class BaseApiController {

    private final ApiResponseFactory responseFactory;

    protected BaseApiController(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    protected <T> ApiResponse<T> ok(String messageCode, T data, Object... args) {
        return responseFactory.ok(messageCode, data, args);
    }
}
