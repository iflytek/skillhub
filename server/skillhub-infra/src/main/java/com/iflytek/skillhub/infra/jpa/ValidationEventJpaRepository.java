package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for validation run events.
 */
@Repository
public interface ValidationEventJpaRepository extends JpaRepository<ValidationEvent, Long>,
        ValidationEventRepository {

    List<ValidationEvent> findByRunIdOrderBySeqAsc(Long runId);

    List<ValidationEvent> findByRunIdAndSeqGreaterThanOrderBySeqAsc(Long runId, int afterSeq);

    List<ValidationEvent> findByRunIdAndSeqIn(Long runId, List<Integer> seqs);

    @Override
    @Query("SELECT MAX(event.seq) FROM ValidationEvent event WHERE event.runId = :runId")
    Optional<Integer> findMaxSeqByRunId(@Param("runId") Long runId);

    void deleteByRunId(Long runId);
}
