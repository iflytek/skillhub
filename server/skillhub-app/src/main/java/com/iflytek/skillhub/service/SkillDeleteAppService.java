package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import com.iflytek.skillhub.search.SearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the API-facing hard-delete flow for whole skills.
 */
@Service
public class SkillDeleteAppService {

    public record DeleteResult(Long skillId, String namespace, String slug, boolean deleted) {
    }

    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillHardDeleteService skillHardDeleteService;
    private final SearchIndexService searchIndexService;

    public SkillDeleteAppService(SkillRepository skillRepository,
                                 NamespaceRepository namespaceRepository,
                                 SkillHardDeleteService skillHardDeleteService,
                                 SearchIndexService searchIndexService) {
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillHardDeleteService = skillHardDeleteService;
        this.searchIndexService = searchIndexService;
    }

    @Transactional
    public DeleteResult deleteSkill(String namespace,
                                    String slug,
                                    String targetOwnerId,
                                    String actorUserId,
                                    AuditRequestContext auditRequestContext) {
        return deleteSkillForActor(namespace, slug, targetOwnerId, actorUserId, auditRequestContext);
    }

    @Transactional
    public DeleteResult deleteSkillById(Long skillId,
                                        String actorUserId,
                                        AuditRequestContext auditRequestContext) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", skill.getNamespaceId()));
        return deleteExistingSkill(skill, namespace.getSlug(), skill.getSlug(), actorUserId, auditRequestContext, false, null);
    }

    @Transactional
    public DeleteResult deleteSkillByIdFromPortal(Long skillId,
                                                  PlatformPrincipal principal,
                                                  AuditRequestContext auditRequestContext) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", skill.getNamespaceId()));
        return deleteExistingSkill(
                skill,
                namespace.getSlug(),
                skill.getSlug(),
                principal.userId(),
                auditRequestContext,
                true,
                principal
        );
    }

    @Transactional
    public DeleteResult deleteSkillFromPortal(String namespace,
                                              String slug,
                                              String targetOwnerId,
                                              PlatformPrincipal principal,
                                              AuditRequestContext auditRequestContext) {
        String normalizedNamespace = normalizeNamespace(namespace);
        List<Skill> candidates = skillRepository.findByNamespaceSlugAndSlug(normalizedNamespace, slug);
        Optional<Skill> target = resolveSkill(candidates, targetOwnerId, principal != null ? principal.userId() : null);
        return target
                .map(skill -> deleteExistingSkill(skill, normalizedNamespace, slug, principal.userId(), auditRequestContext, true, principal))
                .orElseGet(() -> new DeleteResult(null, normalizedNamespace, slug, false));
    }

    private DeleteResult deleteSkillForActor(String namespace,
                                             String slug,
                                             String targetOwnerId,
                                             String actorUserId,
                                             AuditRequestContext auditRequestContext) {
        String normalizedNamespace = normalizeNamespace(namespace);
        List<Skill> candidates = skillRepository.findByNamespaceSlugAndSlug(normalizedNamespace, slug);
        Optional<Skill> target = resolveSkill(candidates, targetOwnerId, null);
        return target
                .map(skill -> deleteExistingSkill(skill, normalizedNamespace, slug, actorUserId, auditRequestContext, false, null))
                .orElseGet(() -> new DeleteResult(null, normalizedNamespace, slug, false));
    }

    private DeleteResult deleteExistingSkill(Skill skill,
                                             String namespace,
                                             String slug,
                                             String actorUserId,
                                             AuditRequestContext auditRequestContext,
                                             boolean enforcePortalOwnership,
                                             PlatformPrincipal principal) {
        if (enforcePortalOwnership && !canDeleteFromPortal(skill, principal)) {
            throw new DomainForbiddenException("error.forbidden");
        }
        searchIndexService.remove(skill.getId());
        skillHardDeleteService.hardDeleteSkill(
                skill,
                namespace,
                actorUserId,
                auditRequestContext != null ? auditRequestContext.clientIp() : null,
                auditRequestContext != null ? auditRequestContext.userAgent() : null
        );
        return new DeleteResult(skill.getId(), namespace, slug, true);
    }

    private String normalizeNamespace(String namespace) {
        if (namespace == null) {
            return null;
        }
        return namespace.startsWith("@") ? namespace.substring(1) : namespace;
    }

    /**
     * Resolves a single skill from candidates sharing the same namespace+slug.
     * If targetOwnerId is provided, matches exactly by owner.
     * When multiple candidates exist and no ownerId is specified, returns empty
     * to force callers to provide an explicit owner for accurate targeting.
     */
    private Optional<Skill> resolveSkill(List<Skill> candidates, String targetOwnerId, String fallbackOwnerId) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        // Explicit owner specified — match exactly
        if (targetOwnerId != null && !targetOwnerId.isBlank()) {
            return candidates.stream()
                    .filter(s -> targetOwnerId.equals(s.getOwnerId()))
                    .findFirst();
        }
        if (fallbackOwnerId != null && !fallbackOwnerId.isBlank()) {
            return candidates.stream()
                    .filter(s -> fallbackOwnerId.equals(s.getOwnerId()))
                    .findFirst();
        }
        // Multiple candidates without explicit owner — require ownerId
        return Optional.empty();
    }

    private boolean canDeleteFromPortal(Skill skill, PlatformPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return principal.platformRoles().contains("SUPER_ADMIN")
                || principal.userId().equals(skill.getOwnerId());
    }
}
