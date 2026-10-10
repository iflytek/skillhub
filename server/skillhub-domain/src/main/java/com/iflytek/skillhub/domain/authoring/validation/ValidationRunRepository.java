package com.iflytek.skillhub.domain.authoring.validation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for validation runs.
 */
public interface ValidationRunRepository {

    ValidationRun save(ValidationRun run);

    Optional<ValidationRun> findById(Long id);

    Optional<ValidationRun> findByIdForUpdate(Long id);

    List<ValidationRun> findByDraftIdOrderByCreatedAtDesc(Long draftId);

    /** Latest run recorded for an exact draft revision, used by the submit gate. */
    Optional<ValidationRun> findFirstByDraftIdAndDraftRevisionOrderByCreatedAtDesc(
            Long draftId, Integer draftRevision);

    /** Runs still in a non-terminal state, used by the maintenance sweep. */
    List<ValidationRun> findByStatusIn(List<ValidationRunStatus> statuses);

    List<ValidationRun> findByStatusInAndCreatedAtBefore(List<ValidationRunStatus> statuses, Instant cutoff);
}
