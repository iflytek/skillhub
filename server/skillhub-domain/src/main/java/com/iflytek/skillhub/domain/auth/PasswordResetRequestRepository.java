package com.iflytek.skillhub.domain.auth;

import java.time.Instant;
import java.util.List;

/**
 * Domain repository contract for local-account password reset verification
 * codes.
 */
public interface PasswordResetRequestRepository {
    PasswordResetRequest save(PasswordResetRequest request);

    List<PasswordResetRequest> findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String userId,
            Instant now
    );
}
