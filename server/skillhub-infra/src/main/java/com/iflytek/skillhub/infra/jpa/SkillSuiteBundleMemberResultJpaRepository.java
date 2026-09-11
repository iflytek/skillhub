package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillSuiteBundleMemberResultJpaRepository
        extends JpaRepository<SkillSuiteBundleMemberResult, Long>, SkillSuiteBundleMemberResultRepository {

    @Override
    default List<SkillSuiteBundleMemberResult> saveAll(List<SkillSuiteBundleMemberResult> members) {
        return saveAllAndFlush(members);
    }
}
