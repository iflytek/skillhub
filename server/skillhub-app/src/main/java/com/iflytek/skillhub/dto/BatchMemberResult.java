package com.iflytek.skillhub.dto;

public record BatchMemberResult(
        String userId,
        String role,
        boolean success,
        String error
) {
    public static BatchMemberResult success(String userId, String role) {
        return new BatchMemberResult(userId, role, true, null);
    }

    public static BatchMemberResult failure(String userId, String role, String error) {
        return new BatchMemberResult(userId, role, false, error);
    }
}
