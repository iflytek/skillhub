package com.iflytek.skillhub.domain.suite;

import java.util.Optional;
import java.time.Instant;

/** Persistence contract for atomic Suite install-plan idempotency claims. */
public interface SkillSuiteInstallOperationRepository {

    int insertIfAbsent(
            String operationId, String clientRequestId, String actorKey, Long suiteId, Long suiteVersionId);

    Optional<SkillSuiteInstallOperation> findByClientRequestIdAndActorKey(String clientRequestId, String actorKey);

    int deleteCreatedBefore(Instant threshold);
}
