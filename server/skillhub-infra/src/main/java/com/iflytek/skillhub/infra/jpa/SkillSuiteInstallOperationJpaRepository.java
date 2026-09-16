package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.SkillSuiteInstallOperation;
import com.iflytek.skillhub.domain.suite.SkillSuiteInstallOperationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/** PostgreSQL-backed atomic claim for Suite install-plan client retry keys. */
@Repository
public interface SkillSuiteInstallOperationJpaRepository
        extends JpaRepository<SkillSuiteInstallOperation, String>, SkillSuiteInstallOperationRepository {

    @Override
    @Modifying
    @Query(value = """
            INSERT INTO skill_suite_install_operation(
                operation_id, client_request_id, actor_key, suite_id, suite_version_id
            )
            VALUES (:operationId, :clientRequestId, :actorKey, :suiteId, :suiteVersionId)
            ON CONFLICT (client_request_id, actor_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("operationId") String operationId,
            @Param("clientRequestId") String clientRequestId,
            @Param("actorKey") String actorKey,
            @Param("suiteId") Long suiteId,
            @Param("suiteVersionId") Long suiteVersionId);

    @Override
    java.util.Optional<SkillSuiteInstallOperation> findByClientRequestIdAndActorKey(
            String clientRequestId, String actorKey);

    @Override
    @Modifying
    @Query("DELETE FROM SkillSuiteInstallOperation operation WHERE operation.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") Instant threshold);
}
