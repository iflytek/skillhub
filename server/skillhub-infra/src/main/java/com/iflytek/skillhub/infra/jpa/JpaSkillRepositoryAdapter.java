package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Primary JPA-backed adapter that fulfills the domain-level {@link SkillRepository} contract.
 */
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
    public List<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug) {
        return delegate.findByNamespaceIdAndSlug(namespaceId, slug);
    }

    @Override
    public Optional<Skill> findByNamespaceIdAndSlugAndOwnerId(Long namespaceId, String slug, String ownerId) {
        return delegate.findByNamespaceIdAndSlugAndOwnerId(namespaceId, slug, ownerId);
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
    public void delete(Skill skill) {
        jpaDelegate.delete(skill);
    }

    @Override
    public List<Skill> findByOwnerId(String ownerId) {
        return delegate.findByOwnerId(ownerId);
    }

    @Override
    public Page<Skill> findByOwnerId(String ownerId, Pageable pageable) {
        return delegate.findByOwnerId(ownerId, pageable);
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
