package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.idempotency.IdempotencyRecord;
import com.iflytek.skillhub.domain.idempotency.IdempotencyRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * JPA repository that persists idempotency records and exposes cleanup operations used by
 * background maintenance.
 */
@Repository
public interface JpaIdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String>, IdempotencyRecordRepository {

    @Modifying
    @Query("DELETE FROM IdempotencyRecord i WHERE i.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE IdempotencyRecord i SET i.status = com.iflytek.skillhub.domain.idempotency.IdempotencyStatus.FAILED WHERE i.status = com.iflytek.skillhub.domain.idempotency.IdempotencyStatus.PROCESSING AND i.createdAt < :threshold")
    int markStaleAsFailed(@Param("threshold") Instant threshold);
}
