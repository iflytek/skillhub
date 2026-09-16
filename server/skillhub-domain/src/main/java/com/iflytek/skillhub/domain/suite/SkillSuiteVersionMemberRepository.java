package com.iflytek.skillhub.domain.suite;

import java.util.List;

/** Persistence contract for ordered Suite member snapshots. */
public interface SkillSuiteVersionMemberRepository {
    List<SkillSuiteVersionMember> findBySuiteVersionIdOrderByPosition(Long suiteVersionId);
    List<SkillSuiteVersionMember> saveAll(List<SkillSuiteVersionMember> members);
    void deleteBySuiteVersionId(Long suiteVersionId);
}
