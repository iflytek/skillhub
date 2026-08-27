package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.security.ScanTaskOutbox;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScanTaskOutboxJpaRepository extends JpaRepository<ScanTaskOutbox, Long>, ScanTaskOutboxRepository {
    @Override
    @Query(value = """
            SELECT * FROM scan_task_outbox
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'SENDING' AND lease_until < :now)
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ScanTaskOutbox> findDispatchable(@Param("now") Instant now, @Param("limit") int limit);

    @Override
    @Modifying
    @Query("DELETE FROM ScanTaskOutbox o WHERE o.status = com.iflytek.skillhub.domain.security.ScanTaskOutboxStatus.SENT AND o.updatedAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") Instant cutoff);

    @Override
    int deleteByVersionId(Long versionId);
}