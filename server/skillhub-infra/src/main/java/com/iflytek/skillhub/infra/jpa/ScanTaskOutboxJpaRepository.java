package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.security.ScanTaskOutbox;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxRepository;
import com.iflytek.skillhub.domain.security.ScanTaskOutboxStatus;
import org.springframework.data.domain.PageRequest;
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
    default List<ScanTaskOutbox> findPendingDue(Instant now, int limit) {
        return findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                ScanTaskOutboxStatus.PENDING, now, PageRequest.of(0, limit));
    }

    @Override
    default List<ScanTaskOutbox> findExpiredLeases(Instant now, int limit) {
        return findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
                ScanTaskOutboxStatus.SENDING, now, PageRequest.of(0, limit));
    }

    @Override
    @Modifying
    @Query("DELETE FROM ScanTaskOutbox o WHERE o.status = com.iflytek.skillhub.domain.security.ScanTaskOutboxStatus.SENT AND o.updatedAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") Instant cutoff);

    @Override
    int deleteByVersionId(Long versionId);

    List<ScanTaskOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            ScanTaskOutboxStatus status, Instant now, org.springframework.data.domain.Pageable pageable);

    List<ScanTaskOutbox> findByStatusAndLeaseUntilBeforeOrderByCreatedAtAsc(
            ScanTaskOutboxStatus status, Instant now, org.springframework.data.domain.Pageable pageable);
}