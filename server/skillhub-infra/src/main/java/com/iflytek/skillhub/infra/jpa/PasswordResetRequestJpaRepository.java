package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.auth.PasswordResetRequest;
import com.iflytek.skillhub.domain.auth.PasswordResetRequestRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA-backed repository for password-reset verification-code requests.
 */
public interface PasswordResetRequestJpaRepository
        extends JpaRepository<PasswordResetRequest, Long>, PasswordResetRequestRepository {

    List<PasswordResetRequest> findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String userId,
            Instant now
    );
}
