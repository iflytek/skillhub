package com.iflytek.skillhub.domain.suite.bundle;

import java.util.List;

public interface SkillSuiteBundleMemberResultRepository {
    List<SkillSuiteBundleMemberResult> saveAll(List<SkillSuiteBundleMemberResult> members);
    List<SkillSuiteBundleMemberResult> findByOperationIdOrderByPosition(String operationId);
}
