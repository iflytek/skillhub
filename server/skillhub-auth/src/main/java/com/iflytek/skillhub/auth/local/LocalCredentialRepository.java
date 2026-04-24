package com.iflytek.skillhub.auth.local;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for username-password credentials linked to platform user accounts.
 */
@Repository
public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {

    Optional<LocalCredential> findByUsernameIgnoreCase(String username);

    Optional<LocalCredential> findByUserId(String userId);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * 更新本地凭证的失败尝试次数和锁定截止时间。
     *
     * @param id             凭证ID
     * @param failedAttempts 失败尝试次数
     * @param lockedUntil    锁定截止时间，如果为null表示不锁定
     */
    @Modifying
    @Query("UPDATE LocalCredential c SET c.failedAttempts = :failedAttempts, c.lockedUntil = :lockedUntil WHERE c.id = :id")
    void updateFailedAttemptsAndLockedUntil(
            Long id,
            int failedAttempts,
            Instant lockedUntil
    );

}
