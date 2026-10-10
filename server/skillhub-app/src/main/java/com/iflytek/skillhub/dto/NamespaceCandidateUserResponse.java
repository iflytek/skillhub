package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.user.UserAccount;

public record NamespaceCandidateUserResponse(
        String userId,
        String displayName,
        String email,
        String status
) {
    public static NamespaceCandidateUserResponse from(UserAccount user) {
        return new NamespaceCandidateUserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getStatus().name()
        );
    }
}
