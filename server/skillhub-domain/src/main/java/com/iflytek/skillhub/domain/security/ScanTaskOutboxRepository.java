package com.iflytek.skillhub.domain.security;

import java.time.Instant;
import java.util.List;

public interface ScanTaskOutboxRepository {
    ScanTaskOutbox save(ScanTaskOutbox outbox);
    List<ScanTaskOutbox> findDispatchable(Instant now, int limit);
    int deleteSentBefore(Instant cutoff);
    int deleteByVersionId(Long versionId);
}
