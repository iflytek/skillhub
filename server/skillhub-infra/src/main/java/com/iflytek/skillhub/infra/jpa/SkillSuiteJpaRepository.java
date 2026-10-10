package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for Suite containers. */
@Repository
public interface SkillSuiteJpaRepository extends JpaRepository<SkillSuite, Long>, SkillSuiteRepository {

    @Override
    @Modifying
    @Transactional
    @Query("UPDATE SkillSuite suite SET suite.installRequestCount = suite.installRequestCount + 1 "
            + "WHERE suite.id = :suiteId")
    void incrementInstallRequestCount(@Param("suiteId") Long suiteId);
}
