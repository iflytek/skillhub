package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunRepository;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for validation runs.
 *
 * <p>The claim query uses explicit {@code FOR UPDATE} SQL for the same H2 PostgreSQL
 * compatibility reason documented on {@link SkillVersionJpaRepository}.
 */
@Repository
public interface ValidationRunJpaRepository extends JpaRepository<ValidationRun, Long>,
        ValidationRunRepository {

    @Override
    @Query(value = "SELECT * FROM validation_run WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<ValidationRun> findByIdForUpdate(@Param("id") Long id);

    List<ValidationRun> findByDraftIdOrderByCreatedAtDesc(Long draftId);

    Optional<ValidationRun> findFirstByDraftIdAndDraftRevisionOrderByCreatedAtDesc(
            Long draftId, Integer draftRevision);

    List<ValidationRun> findByStatusIn(List<ValidationRunStatus> statuses);

    List<ValidationRun> findByStatusInAndCreatedAtBefore(
            List<ValidationRunStatus> statuses, Instant cutoff);
}
