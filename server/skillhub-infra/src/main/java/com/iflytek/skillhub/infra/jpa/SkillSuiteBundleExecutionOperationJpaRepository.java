package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillSuiteBundleExecutionOperationJpaRepository
        extends JpaRepository<SkillSuiteBundleExecutionOperation, String>,
        SkillSuiteBundleExecutionOperationRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT operation FROM SkillSuiteBundleExecutionOperation operation WHERE operation.operationId = :id")
    java.util.Optional<SkillSuiteBundleExecutionOperation> findByIdForUpdate(@Param("id") String operationId);
}
