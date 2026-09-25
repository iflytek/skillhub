package com.iflytek.skillhub.domain.authoring.validation;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for validation findings.
 */
public interface ValidationFindingRepository {

    ValidationFinding save(ValidationFinding finding);

    Optional<ValidationFinding> findById(Long id);

    List<ValidationFinding> findByRunId(Long runId);

    List<ValidationFinding> findByRunIdAndStatus(Long runId, FindingStatus status);

    List<ValidationFinding> findByRunIdAndStatusAndLayer(Long runId, FindingStatus status, ValidationLayer layer);

    List<ValidationFinding> findByRunIdIn(List<Long> runIds);

    void deleteByRunId(Long runId);
}
