package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillSuiteBundleExecutionOperationJpaRepository
        extends JpaRepository<SkillSuiteBundleExecutionOperation, String>,
        SkillSuiteBundleExecutionOperationRepository {
}
