package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillGovernanceService {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final AuditLogService auditLogService;

    public SkillGovernanceService(SkillRepository skillRepository,
                                  SkillVersionRepository skillVersionRepository,
                                  AuditLogService auditLogService) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Skill hideSkill(Long skillId, String actorUserId, String clientIp, String userAgent, String reason) {
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
        skill.setHidden(true);
        skill.setHiddenAt(LocalDateTime.now());
        skill.setHiddenBy(actorUserId);
        skill.setUpdatedBy(actorUserId);
        Skill saved = skillRepository.save(skill);
        auditLogService.record(actorUserId, "HIDE_SKILL", "SKILL", skillId, null, clientIp, userAgent, jsonReason(reason));
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
    public SkillVersion yankVersion(Long versionId, String actorUserId, String clientIp, String userAgent, String reason) {
        SkillVersion version = skillVersionRepository.findById(versionId)
            .orElseThrow(() -> new DomainNotFoundException("error.skill.version.notFound", versionId));
        version.setStatus(SkillVersionStatus.YANKED);
        version.setYankedAt(LocalDateTime.now());
        version.setYankedBy(actorUserId);
        version.setYankReason(reason);
        SkillVersion saved = skillVersionRepository.save(version);
        auditLogService.record(actorUserId, "YANK_SKILL_VERSION", "SKILL_VERSION", versionId, null, clientIp, userAgent, jsonReason(reason));
        return saved;
    }

    private String jsonReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}";
    }
}
