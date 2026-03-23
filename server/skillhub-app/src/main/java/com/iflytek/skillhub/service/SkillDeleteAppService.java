package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import com.iflytek.skillhub.search.SearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the API-facing hard-delete flow for whole skills.
 */
@Service
public class SkillDeleteAppService {

    public record DeleteResult(Long skillId, String namespace, String slug, boolean deleted) {
    }

    private final SkillRepository skillRepository;
    private final SkillHardDeleteService skillHardDeleteService;
    private final SearchIndexService searchIndexService;

    public SkillDeleteAppService(SkillRepository skillRepository,
                                 SkillHardDeleteService skillHardDeleteService,
                                 SearchIndexService searchIndexService) {
        this.skillRepository = skillRepository;
        this.skillHardDeleteService = skillHardDeleteService;
        this.searchIndexService = searchIndexService;
    }

    @Transactional
    public DeleteResult deleteSkill(String namespace,
                                    String slug,
                                    String actorUserId,
                                    AuditRequestContext auditRequestContext) {
        return deleteSkillForActor(namespace, slug, actorUserId, auditRequestContext);
    }

    @Transactional
    public DeleteResult deleteSkillFromPortal(String namespace,
                                              String slug,
                                              PlatformPrincipal principal,
                                              AuditRequestContext auditRequestContext) {
        String normalizedNamespace = normalizeNamespace(namespace);
        return skillRepository.findByNamespaceSlugAndSlug(normalizedNamespace, slug)
                .map(skill -> deleteExistingSkill(skill, normalizedNamespace, slug, principal.userId(), auditRequestContext, true, principal))
                .orElseGet(() -> new DeleteResult(null, normalizedNamespace, slug, false));
    }

    private DeleteResult deleteSkillForActor(String namespace,
                                             String slug,
                                             String actorUserId,
                                             AuditRequestContext auditRequestContext) {
        String normalizedNamespace = normalizeNamespace(namespace);
        return skillRepository.findByNamespaceSlugAndSlug(normalizedNamespace, slug)
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

    private boolean canDeleteFromPortal(Skill skill, PlatformPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return principal.platformRoles().contains("SUPER_ADMIN")
                || principal.userId().equals(skill.getOwnerId());
    }
}
