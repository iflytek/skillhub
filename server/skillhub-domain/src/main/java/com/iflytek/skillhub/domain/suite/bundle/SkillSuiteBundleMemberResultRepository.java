package com.iflytek.skillhub.domain.suite.bundle;

import java.util.Collection;
import java.util.List;

public interface SkillSuiteBundleMemberResultRepository {
    List<SkillSuiteBundleMemberResult> saveAll(List<SkillSuiteBundleMemberResult> members);
    void flush();
    List<SkillSuiteBundleMemberResult> findByOperationIdOrderByPosition(String operationId);
    List<SkillSuiteBundleMemberResult> findByOperationIdInOrderByOperationIdAscPositionAsc(
            Collection<String> operationIds);
    List<SkillSuiteBundleMemberResult> findByOperationIdOrderByPositionForUpdate(String operationId);
    List<SkillSuiteBundleMemberResult> findBySkillVersionId(Long skillVersionId);
}
