package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStats;
import com.iflytek.skillhub.domain.skill.SkillVersionStatsRepository;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Primary JPA-backed adapter for per-version counters that avoids database-specific upsert SQL.
 */
@Repository
@Primary
public class JpaSkillVersionStatsRepositoryAdapter implements SkillVersionStatsRepository {

    private final SkillVersionStatsJpaRepository delegate;

    public JpaSkillVersionStatsRepositoryAdapter(SkillVersionStatsJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<SkillVersionStats> findBySkillVersionId(Long skillVersionId) {
        return delegate.findById(skillVersionId);
    }

    @Override
    @Transactional
    public void incrementDownloadCount(Long skillVersionId, Long skillId) {
        int updated = delegate.incrementExistingDownloadCount(skillVersionId);
        if (updated > 0) {
            return;
        }

        int inserted = delegate.insertInitialDownloadCount(skillVersionId, skillId);
        if (inserted > 0) {
            return;
        }

        delegate.incrementExistingDownloadCount(skillVersionId);
    }

    @Override
    public void deleteBySkillId(Long skillId) {
        delegate.deleteBySkillId(skillId);
    }
}
