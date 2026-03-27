package com.iflytek.skillhub.domain.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository {
    PasswordResetRequest save(PasswordResetRequest request);
    Optional<PasswordResetRequest> findById(Long id);
    List<PasswordResetRequest> findAllPendingNotExpired(Instant now);
    void invalidatePendingRequests(String userId);
}
