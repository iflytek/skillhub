package com.iflytek.skillhub.dto;

import java.util.List;

public record BatchMemberResponse(
        int totalCount,
        int successCount,
        int failureCount,
        List<BatchMemberResult> results
) {}
