package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SkillLabelAppService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final VisibilityChecker visibilityChecker;
    private final LabelDefinitionService labelDefinitionService;
    private final SkillLabelService skillLabelService;
    private final LabelLocalizationService labelLocalizationService;
    private final RbacService rbacService;
    private final AuditLogService auditLogService;
    private final LabelSearchSyncService labelSearchSyncService;

    public SkillLabelAppService(NamespaceRepository namespaceRepository,
                                SkillRepository skillRepository,
                                VisibilityChecker visibilityChecker,
                                LabelDefinitionService labelDefinitionService,
                                SkillLabelService skillLabelService,
                                LabelLocalizationService labelLocalizationService,
                                RbacService rbacService,
                                AuditLogService auditLogService,
                                LabelSearchSyncService labelSearchSyncService) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.visibilityChecker = visibilityChecker;
        this.labelDefinitionService = labelDefinitionService;
        this.skillLabelService = skillLabelService;
        this.labelLocalizationService = labelLocalizationService;
        this.rbacService = rbacService;
        this.auditLogService = auditLogService;
        this.labelSearchSyncService = labelSearchSyncService;
    }

    public List<SkillLabelDto> listSkillLabels(String namespaceSlug,
                                               String skillSlug,
                                               String userId,
                                               Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = resolveSkillForRead(namespaceSlug, skillSlug, userId, userNsRoles, platformRoles(userId));
        return toDtos(skillLabelService.listSkillLabels(skill.getId()));
    }

    public List<SkillLabelDto> listSkillLabelsBySkillId(Long skillId) {
        return toDtos(skillLabelService.listSkillLabels(skillId));
    }

    @Transactional
    public SkillLabelDto attachLabel(String namespaceSlug,
                                     String skillSlug,
                                     String labelSlug,
                                     String userId,
                                     Map<Long, NamespaceRole> userNsRoles,
                                     AuditRequestContext auditContext) {
        Skill skill = resolveSkill(namespaceSlug, skillSlug);
        SkillLabel attached = skillLabelService.attachLabel(
                skill.getId(),
                labelSlug,
                userId,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        afterCommit(() -> labelSearchSyncService.rebuildSkill(skill.getId()));
        recordAudit("SKILL_LABEL_ATTACH", userId, skill.getId(), auditContext, "{\"labelSlug\":\"" + labelSlug + "\"}");
        return toDtos(List.of(attached)).getFirst();
    }

    @Transactional
    public MessageResponse detachLabel(String namespaceSlug,
                                       String skillSlug,
                                       String labelSlug,
                                       String userId,
                                       Map<Long, NamespaceRole> userNsRoles,
                                       AuditRequestContext auditContext) {
        Skill skill = resolveSkill(namespaceSlug, skillSlug);
        skillLabelService.detachLabel(
                skill.getId(),
                labelSlug,
                userId,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        afterCommit(() -> labelSearchSyncService.rebuildSkill(skill.getId()));
        recordAudit("SKILL_LABEL_DETACH", userId, skill.getId(), auditContext, "{\"labelSlug\":\"" + labelSlug + "\"}");
        return new MessageResponse("Label detached");
    }

    private List<SkillLabelDto> toDtos(List<SkillLabel> skillLabels) {
        if (skillLabels.isEmpty()) {
            return List.of();
        }
        List<Long> labelIds = skillLabels.stream()
                .map(SkillLabel::getLabelId)
                .distinct()
                .toList();
        Map<Long, LabelDefinition> definitionsById = labelDefinitionService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(LabelDefinition::getId, Function.identity()));
        Map<Long, List<LabelTranslation>> translationsByLabelId = labelDefinitionService.listTranslationsByLabelIds(labelIds);
        return skillLabels.stream()
                .filter(skillLabel -> definitionsById.containsKey(skillLabel.getLabelId()))
                .map(skillLabel -> {
                    LabelDefinition definition = definitionsById.get(skillLabel.getLabelId());
                    return new SkillLabelDto(
                            definition.getSlug(),
                            definition.getType().name(),
                            labelLocalizationService.resolveDisplayName(
                                    definition.getSlug(),
                                    translationsByLabelId.getOrDefault(definition.getId(), List.of()))
                    );
                })
                .sorted(java.util.Comparator.comparing(SkillLabelDto::type).thenComparing(SkillLabelDto::slug))
                .toList();
    }

    private Skill resolveSkill(String namespaceSlug, String skillSlug) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespaceSlug));
        return skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug).stream()
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));
    }

    private Skill resolveSkillForRead(String namespaceSlug,
                                      String skillSlug,
                                      String userId,
                                      Map<Long, NamespaceRole> userNsRoles,
                                      Set<String> platformRoles) {
        Skill skill = resolveSkill(namespaceSlug, skillSlug);
        if (platformRoles.contains("SUPER_ADMIN")) {
            return skill;
        }
        if (!visibilityChecker.canAccess(skill, userId, normalizeRoles(userNsRoles))) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }
        return skill;
    }

    private Map<Long, NamespaceRole> normalizeRoles(Map<Long, NamespaceRole> userNsRoles) {
        return userNsRoles != null ? userNsRoles : Map.of();
    }

    private Set<String> platformRoles(String userId) {
        return userId == null ? Set.of() : rbacService.getUserRoleCodes(userId);
    }

    private void recordAudit(String action,
                             String userId,
                             Long targetId,
                             AuditRequestContext auditContext,
                             String detailJson) {
        auditLogService.record(
                userId,
                action,
                "SKILL",
                targetId,
                MDC.get("requestId"),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }

    private void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
