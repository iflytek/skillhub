package com.iflytek.skillhub.dto;

public record MergeApprovalDetailsResponse(
    Long mergeRequestId,
    String primaryUserId,
    String primaryDisplayName,
    String expiresAt
) {}
