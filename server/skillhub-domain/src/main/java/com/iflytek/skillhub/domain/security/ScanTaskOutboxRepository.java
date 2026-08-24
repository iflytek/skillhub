package com.iflytek.skillhub.domain.security;

import java.time.Instant;
import java.util.List;

public interface ScanTaskOutboxRepository {
    ScanTaskOutbox save(ScanTaskOutbox outbox);
    ScanTaskOutbox saveAndFlush(ScanTaskOutbox outbox);
    List<ScanTaskOutbox> findPendingDue(Instant now, int limit);
    List<ScanTaskOutbox> findExpiredLeases(Instant now, int limit);
    int deleteSentBefore(Instant cutoff);
    int deleteByVersionId(Long versionId);
}
