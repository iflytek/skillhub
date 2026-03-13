package com.iflytek.skillhub.dto;

public record MergeInitiateResponse(
    Long mergeRequestId,
    String secondaryUserId,
    String verificationToken,
    String expiresAt
) {}
