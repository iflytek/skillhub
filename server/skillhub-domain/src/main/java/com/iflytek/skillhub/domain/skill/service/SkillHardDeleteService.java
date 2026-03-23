package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillTagRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatsRepository;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Permanently deletes a skill and all of its persisted artifacts so the slug
 * may be uploaded again without residual conflicts.
 */
@Service
public class SkillHardDeleteService {

    private static final Logger log = LoggerFactory.getLogger(SkillHardDeleteService.class);

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillTagRepository skillTagRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillStarRepository skillStarRepository;
    private final SkillRatingRepository skillRatingRepository;
    private final SkillReportRepository skillReportRepository;
    private final SkillVersionStatsRepository skillVersionStatsRepository;
    private final ObjectStorageService objectStorageService;
    private final SkillStorageDeletionCompensationService compensationService;
    private final SecurityScanService securityScanService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public SkillHardDeleteService(SkillRepository skillRepository,
                                  SkillVersionRepository skillVersionRepository,
                                  SkillFileRepository skillFileRepository,
                                  SkillTagRepository skillTagRepository,
                                  ReviewTaskRepository reviewTaskRepository,
                                  PromotionRequestRepository promotionRequestRepository,
                                  SkillStarRepository skillStarRepository,
                                  SkillRatingRepository skillRatingRepository,
                                  SkillReportRepository skillReportRepository,
                                  SkillVersionStatsRepository skillVersionStatsRepository,
                                  ObjectStorageService objectStorageService,
                                  SkillStorageDeletionCompensationService compensationService,
                                  SecurityScanService securityScanService,
                                  AuditLogService auditLogService,
                                  ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.skillTagRepository = skillTagRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillStarRepository = skillStarRepository;
        this.skillRatingRepository = skillRatingRepository;
        this.skillReportRepository = skillReportRepository;
        this.skillVersionStatsRepository = skillVersionStatsRepository;
        this.objectStorageService = objectStorageService;
        this.compensationService = compensationService;
        this.securityScanService = securityScanService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void hardDeleteSkill(Skill skill, String namespaceSlug, String actorUserId, String clientIp, String userAgent) {
        List<SkillVersion> versions = skillVersionRepository.findBySkillId(skill.getId());
        List<Long> versionIds = versions.stream().map(SkillVersion::getId).toList();

        List<String> storageKeys = new ArrayList<>();
        for (SkillVersion version : versions) {
            List<SkillFile> files = skillFileRepository.findByVersionId(version.getId());
            files.stream()
                    .map(SkillFile::getStorageKey)
                    .filter(key -> key != null && !key.isBlank())
                    .forEach(storageKeys::add);
            storageKeys.add(buildBundleStorageKey(skill.getId(), version.getId()));
        }
        deleteStorageAfterCommit(skill, namespaceSlug, storageKeys);

        skill.setLatestVersionId(null);
        skill.setUpdatedBy(actorUserId);
        skillRepository.save(skill);
        skillRepository.flush();

        if (!versionIds.isEmpty()) {
            reviewTaskRepository.deleteBySkillVersionIdIn(versionIds);
        }
        promotionRequestRepository.deleteBySourceSkillIdOrTargetSkillId(skill.getId(), skill.getId());
        skillTagRepository.deleteBySkillId(skill.getId());
        skillStarRepository.deleteBySkillId(skill.getId());
        skillRatingRepository.deleteBySkillId(skill.getId());
        skillReportRepository.deleteBySkillId(skill.getId());
        skillVersionStatsRepository.deleteBySkillId(skill.getId());

        for (Long versionId : versionIds) {
            securityScanService.softDeleteByVersionId(versionId);
            skillFileRepository.deleteByVersionId(versionId);
        }
        skillVersionRepository.deleteBySkillId(skill.getId());
        skillRepository.delete(skill);

        auditLogService.record(
                actorUserId,
                "DELETE_SKILL_HARD",
                "SKILL",
                skill.getId(),
                null,
                clientIp,
                userAgent,
                toAuditPayload(skill)
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
            log.error("Failed to delete storage objects after hard delete commit [keys={}]", storageKeys, ex);
        }
    }

    private String buildBundleStorageKey(Long skillId, Long versionId) {
        return String.format("packages/%d/%d/bundle.zip", skillId, versionId);
    }

    private String toAuditPayload(Skill skill) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("namespaceId", skill.getNamespaceId());
        payload.put("slug", skill.getSlug());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize hard-delete audit payload", e);
        }
    }
}
