package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.dto.AdminLabelCreateRequest;
import com.iflytek.skillhub.dto.AdminLabelUpdateRequest;
import com.iflytek.skillhub.dto.LabelDefinitionResponse;
import com.iflytek.skillhub.dto.LabelSortOrderUpdateRequest;
import com.iflytek.skillhub.dto.LabelTranslationResponse;
import java.util.List;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class LabelAdminAppService {

    private final LabelDefinitionService labelDefinitionService;
    private final SkillLabelService skillLabelService;
    private final AuditLogService auditLogService;
    private final RbacService rbacService;
    private final LabelSearchSyncService labelSearchSyncService;

    public LabelAdminAppService(LabelDefinitionService labelDefinitionService,
                                SkillLabelService skillLabelService,
                                AuditLogService auditLogService,
                                RbacService rbacService,
                                LabelSearchSyncService labelSearchSyncService) {
        this.labelDefinitionService = labelDefinitionService;
        this.skillLabelService = skillLabelService;
        this.auditLogService = auditLogService;
        this.rbacService = rbacService;
        this.labelSearchSyncService = labelSearchSyncService;
    }

    public List<LabelDefinitionResponse> listAll() {
        return labelDefinitionService.listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LabelDefinitionResponse create(AdminLabelCreateRequest request,
                                          String userId,
                                          AuditRequestContext auditContext) {
        LabelDefinition labelDefinition = labelDefinitionService.create(
                request.slug(),
                request.type(),
                request.visibleInFilter(),
                request.sortOrder(),
                toTranslations(request.translations()),
                userId,
                platformRoles(userId)
        );
        recordAudit("LABEL_CREATE", userId, labelDefinition.getId(), auditContext, "{\"slug\":\"" + labelDefinition.getSlug() + "\"}");
        return toResponse(labelDefinition);
    }

    @Transactional
    public LabelDefinitionResponse update(String slug,
                                          AdminLabelUpdateRequest request,
                                          String userId,
                                          AuditRequestContext auditContext) {
        LabelDefinition existing = labelDefinitionService.getBySlug(slug);
        List<Long> affectedSkillIds = skillLabelService.listByLabelId(existing.getId()).stream()
                .map(com.iflytek.skillhub.domain.label.SkillLabel::getSkillId)
                .distinct()
                .toList();
        LabelDefinition updated = labelDefinitionService.update(
                slug,
                request.type(),
                request.visibleInFilter(),
                request.sortOrder(),
                toTranslations(request.translations()),
                platformRoles(userId)
        );
        if (!affectedSkillIds.isEmpty()) {
            afterCommit(() -> labelSearchSyncService.rebuildSkills(affectedSkillIds));
        }
        recordAudit("LABEL_UPDATE", userId, updated.getId(), auditContext, "{\"slug\":\"" + updated.getSlug() + "\"}");
        return toResponse(updated);
    }

    @Transactional
    public void delete(String slug, String userId, AuditRequestContext auditContext) {
        LabelDefinition existing = labelDefinitionService.getBySlug(slug);
        List<Long> affectedSkillIds = skillLabelService.listByLabelId(existing.getId()).stream()
                .map(com.iflytek.skillhub.domain.label.SkillLabel::getSkillId)
                .distinct()
                .toList();
        labelDefinitionService.delete(slug, platformRoles(userId));
        if (!affectedSkillIds.isEmpty()) {
            afterCommit(() -> labelSearchSyncService.rebuildSkills(affectedSkillIds));
        }
        recordAudit("LABEL_DELETE", userId, existing.getId(), auditContext, "{\"slug\":\"" + slug + "\"}");
    }

    @Transactional
    public List<LabelDefinitionResponse> updateSortOrder(LabelSortOrderUpdateRequest request,
                                                         String userId,
                                                         AuditRequestContext auditContext) {
        List<LabelDefinitionService.LabelSortOrderUpdate> updates = request.items().stream()
                .map(item -> {
                    LabelDefinition labelDefinition = labelDefinitionService.getBySlug(item.slug());
                    return new LabelDefinitionService.LabelSortOrderUpdate(labelDefinition.getId(), item.sortOrder());
                })
                .toList();
        List<LabelDefinitionResponse> responses = labelDefinitionService.updateSortOrders(updates, platformRoles(userId)).stream()
                .map(this::toResponse)
                .toList();
        recordAudit("LABEL_SORT_ORDER_UPDATE", userId, null, auditContext, "{\"count\":" + request.items().size() + "}");
        return responses;
    }

    private List<LabelTranslation> toTranslations(List<com.iflytek.skillhub.dto.LabelTranslationItemRequest> items) {
        return items.stream()
                .map(item -> new LabelTranslation(null, item.locale(), item.displayName()))
                .toList();
    }

    private LabelDefinitionResponse toResponse(LabelDefinition labelDefinition) {
        List<LabelTranslationResponse> translations = labelDefinitionService.listTranslations(labelDefinition.getId()).stream()
                .map(translation -> new LabelTranslationResponse(translation.getLocale(), translation.getDisplayName()))
                .toList();
        return new LabelDefinitionResponse(
                labelDefinition.getSlug(),
                labelDefinition.getType().name(),
                labelDefinition.isVisibleInFilter(),
                labelDefinition.getSortOrder(),
                translations,
                labelDefinition.getCreatedAt()
        );
    }

    private Set<String> platformRoles(String userId) {
        return rbacService.getUserRoleCodes(userId);
    }

    private void recordAudit(String action,
                             String userId,
                             Long targetId,
                             AuditRequestContext auditContext,
                             String detailJson) {
        auditLogService.record(
                userId,
                action,
                "LABEL",
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
