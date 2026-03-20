package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
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
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permanently deletes a skill and all of its persisted artifacts so the slug
 * may be uploaded again without residual conflicts.
 */
@Service
public class SkillHardDeleteService {

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
    private final AuditLogService auditLogService;

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
                                  AuditLogService auditLogService) {
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
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void hardDeleteSkill(Skill skill, String actorUserId, String clientIp, String userAgent) {
        List<SkillVersion> versions = skillVersionRepository.findBySkillId(skill.getId());
        List<Long> versionIds = versions.stream().map(SkillVersion::getId).toList();

        List<String> storageKeys = new ArrayList<>();
        for (SkillVersion version : versions) {
            List<SkillFile> files = skillFileRepository.findByVersionId(version.getId());
            files.stream()
                    .map(SkillFile::getStorageKey)
                    .filter(key -> key != null && !key.isBlank())
                    .forEach(storageKeys::add);
            storageKeys.add(String.format("packages/%d/%d/bundle.zip", skill.getId(), version.getId()));
        }
        if (!storageKeys.isEmpty()) {
            objectStorageService.deleteObjects(storageKeys);
        }

        skill.setLatestVersionId(null);
        skill.setUpdatedBy(actorUserId);
        skillRepository.save(skill);

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
                "{\"namespaceId\":" + skill.getNamespaceId() + ",\"slug\":\"" + escapeJson(skill.getSlug()) + "\"}"
        );
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
