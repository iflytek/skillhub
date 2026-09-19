package com.iflytek.skillhub.domain.authoring.validation;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for persisted validation events.
 */
public interface ValidationEventRepository {

    ValidationEvent save(ValidationEvent event);

    List<ValidationEvent> findByRunIdOrderBySeqAsc(Long runId);

    /** Events with seq strictly greater than the cursor, ordered for replay. */
    List<ValidationEvent> findByRunIdAndSeqGreaterThanOrderBySeqAsc(Long runId, int afterSeq);

    List<ValidationEvent> findByRunIdAndSeqIn(Long runId, List<Integer> seqs);

    Optional<Integer> findMaxSeqByRunId(Long runId);

    void deleteByRunId(Long runId);
}
