package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillSuiteBundleMemberResultJpaRepository
        extends JpaRepository<SkillSuiteBundleMemberResult, Long>, SkillSuiteBundleMemberResultRepository {

    @Override
    default List<SkillSuiteBundleMemberResult> saveAll(List<SkillSuiteBundleMemberResult> members) {
        return saveAllAndFlush(members);
    }

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT member FROM SkillSuiteBundleMemberResult member
             WHERE member.operationId = :operationId
             ORDER BY member.position
            """)
    List<SkillSuiteBundleMemberResult> findByOperationIdOrderByPositionForUpdate(
            @Param("operationId") String operationId);
}
