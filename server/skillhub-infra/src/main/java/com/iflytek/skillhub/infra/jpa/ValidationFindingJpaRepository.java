package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFindingRepository;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import com.iflytek.skillhub.domain.authoring.validation.FindingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for validation findings.
 */
@Repository
public interface ValidationFindingJpaRepository extends JpaRepository<ValidationFinding, Long>,
        ValidationFindingRepository {

    Optional<ValidationFinding> findById(Long id);

    List<ValidationFinding> findByRunId(Long runId);

    List<ValidationFinding> findByRunIdAndStatus(Long runId, FindingStatus status);

    List<ValidationFinding> findByRunIdAndStatusAndLayer(
            Long runId, FindingStatus status, ValidationLayer layer);

    List<ValidationFinding> findByRunIdIn(List<Long> runIds);

    void deleteByRunId(Long runId);
}
