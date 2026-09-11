package com.iflytek.skillhub.dto;

public record SkillSuiteBundleOperationResponse(
        String operationId,
        String status,
        boolean replayed
) {
}
