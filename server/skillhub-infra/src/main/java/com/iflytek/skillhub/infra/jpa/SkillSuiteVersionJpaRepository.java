package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** JPA adapter for Suite version snapshots. */
@Repository
public interface SkillSuiteVersionJpaRepository
        extends JpaRepository<SkillSuiteVersion, Long>, SkillSuiteVersionRepository {

    /** Ensure member-only draft edits still conflict with a concurrent lifecycle transition. */
    @Override
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select version from SkillSuiteVersion version where version.id = :id")
    Optional<SkillSuiteVersion> findByIdForDefinitionUpdate(@Param("id") Long id);
}
