package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.event.SkillStatusChangedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Handles governance-oriented mutations on skills and versions, including
 * hiding, archiving, restoring, and destructive cleanup.
 */
@Service
public class SkillGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(SkillGovernanceService.class);

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final ObjectStorageService objectStorageService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityScanService securityScanService;
    private final SkillStorageDeletionCompensationService compensationService;
    private final Clock clock;

    public SkillGovernanceService(SkillRepository skillRepository,
                                  SkillVersionRepository skillVersionRepository,
                                  SkillFileRepository skillFileRepository,
                                  ObjectStorageService objectStorageService,
                                  AuditLogService auditLogService,
                                  ApplicationEventPublisher eventPublisher,
                                  SecurityScanService securityScanService,
                                  SkillStorageDeletionCompensationService compensationService,
                                  Clock clock) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.objectStorageService = objectStorageService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.securityScanService = securityScanService;
        this.compensationService = compensationService;
        this.clock = clock;
    }

    @Transactional
    public Skill hideSkill(Long skillId, String actorUserId, String clientIp, String userAgent, String reason) {
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        skill.setHidden(true);
        skill.setHiddenAt(currentInstant());
        skill.setHiddenBy(actorUserId);
        skill.setUpdatedBy(actorUserId);
        Skill saved = skillRepository.save(skill);
        auditLogService.record(actorUserId, "HIDE_SKILL", "SKILL", skillId, null, clientIp, userAgent, jsonReason(reason));
        return saved;
    }

    @Transactional
    public Skill archiveSkill(Long skillId,
                              String actorUserId,
                              Map<Long, NamespaceRole> userNamespaceRoles,
                              String clientIp,
                              String userAgent,
                              String reason) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        assertCanManageLifecycle(skill, actorUserId, userNamespaceRoles);
        return archiveSkillInternal(skill, actorUserId, clientIp, userAgent, reason);
    }

    @Transactional
    public Skill archiveSkillAsAdmin(Long skillId,
                                     String actorUserId,
                                     String clientIp,
                                     String userAgent,
                                     String reason) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        return archiveSkillInternal(skill, actorUserId, clientIp, userAgent, reason);
    }

    private Skill archiveSkillInternal(Skill skill,
                                       String actorUserId,
                                       String clientIp,
                                       String userAgent,
                                       String reason) {
        SkillStatus previousStatus = skill.getStatus();
        skill.setStatus(SkillStatus.ARCHIVED);
        skill.setUpdatedBy(actorUserId);
        Skill saved = skillRepository.save(skill);
        auditLogService.record(actorUserId, "ARCHIVE_SKILL", "SKILL", skill.getId(), null, clientIp, userAgent, jsonReason(reason));
        eventPublisher.publishEvent(new SkillStatusChangedEvent(skill.getId(), previousStatus, SkillStatus.ARCHIVED));
        return saved;
    }

    @Transactional
    public Skill unhideSkill(Long skillId, String actorUserId, String clientIp, String userAgent) {
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        skill.setHidden(false);
        skill.setHiddenAt(null);
        skill.setHiddenBy(null);
        skill.setUpdatedBy(actorUserId);
        Skill saved = skillRepository.save(skill);
        auditLogService.record(actorUserId, "UNHIDE_SKILL", "SKILL", skillId, null, clientIp, userAgent, null);
        return saved;
    }

    @Transactional
    public Skill unarchiveSkill(Long skillId,
                                String actorUserId,
                                Map<Long, NamespaceRole> userNamespaceRoles,
                                String clientIp,
                                String userAgent) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        assertCanManageLifecycle(skill, actorUserId, userNamespaceRoles);

        SkillStatus previousStatus = skill.getStatus();
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setUpdatedBy(actorUserId);
        Skill saved = skillRepository.save(skill);
        auditLogService.record(actorUserId, "UNARCHIVE_SKILL", "SKILL", skillId, null, clientIp, userAgent, null);
        eventPublisher.publishEvent(new SkillStatusChangedEvent(skillId, previousStatus, SkillStatus.ACTIVE));
        return saved;
    }

    @Transactional
    public void deleteVersion(Skill skill,
                              SkillVersion version,
                              String actorUserId,
                              Map<Long, NamespaceRole> userNamespaceRoles,
                              String clientIp,
                              String userAgent,
                              String namespaceSlug) {
        assertCanManageLifecycle(skill, actorUserId, userNamespaceRoles);
        if (version.getStatus() != SkillVersionStatus.DRAFT
                && version.getStatus() != SkillVersionStatus.REJECTED
                && version.getStatus() != SkillVersionStatus.SCAN_FAILED
                && version.getStatus() != SkillVersionStatus.UPLOADED) {
            throw new DomainBadRequestException("error.skill.version.delete.unsupported", version.getVersion());
        }

        long versionCount = skillVersionRepository.findBySkillId(skill.getId()).size();
        if (versionCount <= 1) {
            throw new DomainBadRequestException("error.skill.version.delete.lastVersion", version.getVersion());
        }

        List<SkillFile> files = skillFileRepository.findByVersionId(version.getId());
        List<String> storageKeys = new ArrayList<>();
        files.stream()
                .map(SkillFile::getStorageKey)
                .filter(key -> key != null && !key.isBlank())
                .forEach(storageKeys::add);
        storageKeys.add(buildBundleStorageKey(skill.getId(), version.getId()));
        deleteStorageAfterCommit(skill, namespaceSlug, storageKeys);
        skillFileRepository.deleteByVersionId(version.getId());
        securityScanService.softDeleteByVersionId(version.getId());
        skillVersionRepository.delete(version);
        if (version.getId().equals(skill.getLatestVersionId())) {
            skill.setLatestVersionId(findLatestPublishedVersionId(skill.getId()));
            skill.setUpdatedBy(actorUserId);
            skillRepository.save(skill);
        }
        auditLogService.record(
                actorUserId,
                "DELETE_SKILL_VERSION",
                "SKILL_VERSION",
                version.getId(),
                null,
                clientIp,
                userAgent,
                "{\"version\":\"" + version.getVersion().replace("\"", "\\\"") + "\"}"
        );
    }

    private void deleteStorageAfterCommit(Skill skill, String namespaceSlug, List<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStorageWithCompensation(skill, namespaceSlug, storageKeys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteStorageWithCompensation(skill, namespaceSlug, storageKeys);
            }
        });
    }

    private void deleteStorageWithCompensation(Skill skill, String namespaceSlug, List<String> storageKeys) {
        try {
            objectStorageService.deleteObjects(storageKeys);
        } catch (RuntimeException ex) {
            compensationService.recordFailure(
                    skill.getId(),
                    namespaceSlug,
                    skill.getSlug(),
                    storageKeys,
                    ex.getMessage()
            );
            log.error("Failed to delete version storage after commit [skillId={}, versionKeys={}]",
                    skill.getId(), storageKeys, ex);
        }
    }

    private String buildBundleStorageKey(Long skillId, Long versionId) {
        return String.format("packages/%d/%d/bundle.zip", skillId, versionId);
    }

    @Transactional
    public SkillVersion withdrawPendingVersion(Skill skill,
                                               SkillVersion version,
                                               String actorUserId) {
        if (version.getStatus() != SkillVersionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("review.withdraw.not_pending", version.getId());
        }
        version.setStatus(SkillVersionStatus.UPLOADED);
        SkillVersion savedVersion = skillVersionRepository.save(version);
        skill.setUpdatedBy(actorUserId);
        skillRepository.save(skill);
        return savedVersion;
    }

    @Transactional
    public SkillVersion yankVersion(Long versionId, String actorUserId, String clientIp, String userAgent, String reason) {
        SkillVersion version = skillVersionRepository.findById(versionId)
            .orElseThrow(() -> new DomainNotFoundException("error.skill.version.notFound", versionId));
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.skill.version.notPublished", version.getVersion());
        }
        version.setStatus(SkillVersionStatus.YANKED);
        version.setYankedAt(currentInstant());
        version.setYankedBy(actorUserId);
        version.setYankReason(reason);
        version.setDownloadReady(false);
        SkillVersion saved = skillVersionRepository.save(version);
        skillRepository.findById(version.getSkillId()).ifPresent(skill -> {
            if (versionId.equals(skill.getLatestVersionId())) {
                skill.setLatestVersionId(findLatestPublishedVersionId(skill.getId()));
                skill.setUpdatedBy(actorUserId);
                skillRepository.save(skill);
            }
        });
        auditLogService.record(actorUserId, "YANK_SKILL_VERSION", "SKILL_VERSION", versionId, null, clientIp, userAgent, jsonReason(reason));
        eventPublisher.publishEvent(new com.iflytek.skillhub.domain.event.SkillVersionYankedEvent(
                version.getSkillId(), versionId, actorUserId));
        return saved;
    }

    private Long findLatestPublishedVersionId(Long skillId) {
        return skillVersionRepository.findBySkillIdAndStatus(skillId, SkillVersionStatus.PUBLISHED).stream()
                .max(java.util.Comparator
                        .comparing(SkillVersion::getPublishedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(SkillVersion::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(SkillVersion::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(SkillVersion::getId)
                .orElse(null);
    }

    private void assertCanManageLifecycle(Skill skill,
                                          String actorUserId,
                                          Map<Long, NamespaceRole> userNamespaceRoles) {
        NamespaceRole namespaceRole = userNamespaceRoles.get(skill.getNamespaceId());
        boolean canManage = skill.getOwnerId().equals(actorUserId)
                || namespaceRole == NamespaceRole.ADMIN
                || namespaceRole == NamespaceRole.OWNER;
        if (!canManage) {
            throw new DomainForbiddenException("error.skill.lifecycle.noPermission");
        }
    }

    private String jsonReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}";
    }

    private Instant currentInstant() {
        return Instant.now(clock);
    }
}
