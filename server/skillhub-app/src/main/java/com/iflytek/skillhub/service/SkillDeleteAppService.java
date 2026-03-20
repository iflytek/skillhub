package com.iflytek.skillhub.service;

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
        String normalizedNamespace = normalizeNamespace(namespace);
        return skillRepository.findByNamespaceSlugAndSlug(normalizedNamespace, slug)
                .map(skill -> deleteExistingSkill(skill, normalizedNamespace, slug, actorUserId, auditRequestContext))
                .orElseGet(() -> new DeleteResult(null, normalizedNamespace, slug, false));
    }

    private DeleteResult deleteExistingSkill(Skill skill,
                                             String namespace,
                                             String slug,
                                             String actorUserId,
                                             AuditRequestContext auditRequestContext) {
        skillHardDeleteService.hardDeleteSkill(
                skill,
                actorUserId,
                auditRequestContext != null ? auditRequestContext.clientIp() : null,
                auditRequestContext != null ? auditRequestContext.userAgent() : null
        );
        searchIndexService.remove(skill.getId());
        return new DeleteResult(skill.getId(), namespace, slug, true);
    }

    private String normalizeNamespace(String namespace) {
        if (namespace == null) {
            return null;
        }
        return namespace.startsWith("@") ? namespace.substring(1) : namespace;
    }
}
