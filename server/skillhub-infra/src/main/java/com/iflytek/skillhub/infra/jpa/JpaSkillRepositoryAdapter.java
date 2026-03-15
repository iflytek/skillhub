package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
public class JpaSkillRepositoryAdapter implements SkillRepository {

    private final SkillJpaRepository delegate;
    private final JpaRepository<Skill, Long> jpaDelegate;

    public JpaSkillRepositoryAdapter(SkillJpaRepository delegate) {
        this.delegate = delegate;
        this.jpaDelegate = delegate;
    }

    @Override
    public Optional<Skill> findById(Long id) {
        return jpaDelegate.findById(id);
    }

    @Override
    public List<Skill> findByIdIn(List<Long> ids) {
        return delegate.findByIdIn(ids);
    }

    @Override
    public List<Skill> findAll() {
        return jpaDelegate.findAll();
    }

    @Override
    public Optional<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug) {
        return delegate.findByNamespaceIdAndSlug(namespaceId, slug);
    }

    @Override
    public List<Skill> findByNamespaceIdAndStatus(Long namespaceId, SkillStatus status) {
        return delegate.findByNamespaceIdAndStatus(namespaceId, status);
    }

    @Override
    public Skill save(Skill skill) {
        return jpaDelegate.save(skill);
    }

    @Override
    public List<Skill> findByOwnerId(String ownerId) {
        return delegate.findByOwnerId(ownerId);
    }

    @Override
    public void incrementDownloadCount(Long skillId) {
        delegate.incrementDownloadCount(skillId);
    }

    @Override
    public List<Skill> findBySlug(String slug) {
        return delegate.findBySlug(slug);
    }

    @Override
    public Optional<Skill> findByNamespaceSlugAndSlug(String namespaceSlug, String slug) {
        return delegate.findByNamespaceSlugAndSlug(namespaceSlug, slug);
    }
}
