package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensation;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationRepository;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class JpaSkillStorageDeletionCompensationRepositoryAdapter
        implements SkillStorageDeletionCompensationRepository {

    private final SkillStorageDeletionCompensationJpaRepository delegate;

    public JpaSkillStorageDeletionCompensationRepositoryAdapter(
            SkillStorageDeletionCompensationJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public SkillStorageDeletionCompensation save(SkillStorageDeletionCompensation compensation) {
        return delegate.save(compensation);
    }

    @Override
    public List<SkillStorageDeletionCompensation> findTop100ByStatusOrderByCreatedAtAsc(
            SkillStorageDeletionCompensationStatus status) {
        return delegate.findTop100ByStatusOrderByCreatedAtAsc(status);
    }
}
