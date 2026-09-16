package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMember;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMemberRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA adapter for ordered Suite member snapshots. */
@Repository
public interface SkillSuiteVersionMemberJpaRepository
        extends JpaRepository<SkillSuiteVersionMember, Long>, SkillSuiteVersionMemberRepository {

    @Override
    @Modifying(flushAutomatically = true)
    @Query("delete from SkillSuiteVersionMember member where member.suiteVersionId = :suiteVersionId")
    void deleteBySuiteVersionId(@Param("suiteVersionId") Long suiteVersionId);

    @Override
    default List<SkillSuiteVersionMember> saveAll(List<SkillSuiteVersionMember> members) {
        return saveAllAndFlush(members);
    }
}
