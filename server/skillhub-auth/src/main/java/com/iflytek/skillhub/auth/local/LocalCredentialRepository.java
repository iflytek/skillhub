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

    @Modifying
    @Query("UPDATE LocalCredential c SET c.failedAttempts = :failedAttempts, c.lockedUntil = :lockedUntil WHERE c.id = :id")
    int updateFailedAttemptsAndLockedUntil(Long id, int failedAttempts, Instant lockedUntil);
}
