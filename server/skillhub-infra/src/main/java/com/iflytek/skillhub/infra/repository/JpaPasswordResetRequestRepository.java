package com.iflytek.skillhub.infra.repository;

import com.iflytek.skillhub.domain.auth.PasswordResetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JpaPasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {
    @Query("SELECT r FROM PasswordResetRequest r WHERE r.consumedAt IS NULL AND r.expiresAt > :now")
    List<PasswordResetRequest> findAllPendingNotExpired(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetRequest r SET r.consumedAt = CURRENT_TIMESTAMP WHERE r.userId = :userId AND r.consumedAt IS NULL")
    void invalidatePendingRequests(@Param("userId") String userId);
}
