package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy;
import com.iflytek.skillhub.domain.event.SkillVisibilityChangedEvent;
import com.iflytek.skillhub.domain.event.BusinessEventSnapshot;
import com.iflytek.skillhub.domain.security.*;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.skill.*;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes access scope without changing the published package or version identity. */
@Service
public class SkillVisibilityService {
    private final SkillOwnerAccessPolicy ownerAccess;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final SkillRepository skills;
    private final SkillVersionRepository versions;
    private final ReviewTaskRepository reviews;
    private final NamespaceRepository namespaces;
    private final ApplicationEventPublisher events;
    private final SecurityScanService scanner;
    private final SecurityAuditRepository audits;

    public SkillVisibilityService(SkillOwnerAccessPolicy ownerAccess, SkillRepository skills, SkillVersionRepository versions,
            ReviewTaskRepository reviews, NamespaceRepository namespaces, ApplicationEventPublisher events,
            SecurityScanService scanner, SecurityAuditRepository audits) {
        this.ownerAccess = ownerAccess;
        this.skills = skills;
        this.versions = versions;
        this.reviews = reviews;
        this.namespaces = namespaces;
        this.events = events;
        this.scanner = scanner;
        this.audits = audits;
    }

    @Transactional
    public ReviewTask change(Long skillId, SkillVisibility target, String actor,
            Map<Long, NamespaceRole> roles, Set<String> platformRoles) {
        Skill skill = lockedSkill(skillId);
        ownerAccess.assertCanWrite(skill, actor, platformRoles);
        assertCanManage(skill, actor, roles, platformRoles);
        assertActive(skill);
        if (target == null || target == skill.getVisibility()) {
            throw new DomainBadRequestException("error.skill.visibility.unchanged");
        }
        assertNoPendingVisibility(skillId);
        if (!versions.findBySkillIdAndStatus(skillId, SkillVersionStatus.PENDING_REVIEW).isEmpty()
                || !versions.findBySkillIdAndStatus(skillId, SkillVersionStatus.SCANNING).isEmpty()) {
            throw new DomainConflictException("error.skill.visibility.pending");
        }
        if (skill.getPendingAvatarId() != null) {
            throw new DomainConflictException("error.skill.visibility.avatarPending");
        }
        if (target.ordinal() > skill.getVisibility().ordinal()) {
            apply(skill, target, actor);
            return null;
        }
        ReviewTask task = ReviewTask.forVisibility(skill, target, actor);
        var previousScan = audits.findLatestActiveByVersionIdAndScannerType(skill.getLatestVersionId(), ScannerType.SKILL_SCANNER);
        if (previousScan.filter(audit -> audit.getTaskId() != null).filter(this::safeScan).isPresent()) {
            task.setVisibilityScanTaskId(previousScan.get().getTaskId());
        } else {
            if (!scanner.isEnabled()) { throw new DomainBadRequestException("error.security.scanner.required"); }
            var version = versions.findById(skill.getLatestVersionId()).orElseThrow();
            if (!version.isBundleReady()) { throw new DomainBadRequestException("error.skill.visibility.scanUnavailable"); }
            task.setVisibilityScanTaskId(scanner.scanPublishedVersion(version, actor).taskId());
        }
        return reviews.save(task);
    }

    public ReviewTask pending(Long skillId, String actor, Map<Long, NamespaceRole> roles, Set<String> platformRoles) {
        Skill skill = skills.findById(skillId).orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillId));
        assertCanManage(skill, actor, roles, platformRoles);
        return reviews.findBySkillIdAndRequestedVisibilityIsNotNullAndStatus(skillId, ReviewTaskStatus.PENDING).orElse(null);
    }

    private void assertCanManage(Skill skill, String actor, Map<Long, NamespaceRole> roles, Set<String> platformRoles) {
        NamespaceRole role = roles.get(skill.getNamespaceId());
        if (!Objects.equals(actor, skill.getOwnerId()) && role != NamespaceRole.OWNER
                && role != NamespaceRole.ADMIN && !platformRoles.contains("SUPER_ADMIN")) {
            throw new DomainForbiddenException("error.skill.lifecycle.noPermission");
        }
    }

    public void assertNoPendingVisibility(Long skillId) {
        if (reviews.existsBySkillIdAndRequestedVisibilityIsNotNullAndStatus(skillId, ReviewTaskStatus.PENDING)) {
            throw new DomainConflictException("error.skill.visibility.pending");
        }
    }

    public Skill lockedSkill(Long id) {
        Skill skill = skills.findLockedById(id).orElseThrow(() -> new DomainNotFoundException("skill.not_found", id));
        if (entityManager != null) { entityManager.refresh(skill); }
        return skill;
    }

    public void validateApproval(ReviewTask task) {
        Skill skill = lockedSkill(task.getSkillId());
        assertActive(skill);
        if (task.getVisibilityScanTaskId() == null || audits
                .findLatestActiveByVersionIdAndScannerType(task.getVisibilityVersionId(), ScannerType.SKILL_SCANNER)
                .filter(audit -> task.getVisibilityScanTaskId().equals(audit.getTaskId()))
                .filter(this::safeScan).isEmpty()) {
            throw new DomainConflictException("error.skill.visibility.scanIncomplete");
        }
        if (skill.getVisibility() != task.getPreviousVisibility()
                || !Objects.equals(skill.getLatestVersionId(), task.getVisibilityVersionId())) {
            throw new DomainConflictException("error.skill.visibility.stale");
        }
    }

    public void approve(ReviewTask task, String actor) {
        validateApproval(task);
        apply(lockedSkill(task.getSkillId()), task.getRequestedVisibility(), actor);
    }

    private void assertActive(Skill skill) {
        Namespace namespace = namespaces.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", skill.getNamespaceId()));
        if (namespace.getStatus() != NamespaceStatus.ACTIVE || skill.getStatus() != SkillStatus.ACTIVE
                || skill.isHidden() || skill.getLatestVersionId() == null
                || versions.findById(skill.getLatestVersionId())
                    .filter(version -> version.getStatus() == SkillVersionStatus.PUBLISHED).isEmpty()) {
            throw new DomainBadRequestException("error.skill.visibility.notPublished");
        }
    }

    private boolean safeScan(SecurityAudit audit) {
        return audit.getScannedAt() != null && audit.getFailureReason() == null && Boolean.TRUE.equals(audit.getIsSafe());
    }

    private void apply(Skill skill, SkillVisibility target, String actor) {
        SkillVisibility previous = skill.getVisibility();
        skill.setVisibility(target);
        skill.setUpdatedBy(actor);
        skills.save(skill);
        events.publishEvent(new SkillVisibilityChangedEvent(skill.getId(), previous, target,
                BusinessEventSnapshot.capture(skill, actor, "visibility")));
    }
}
