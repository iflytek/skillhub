package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillOwnerChangedEvent;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands a skill to another namespace member, for example when its owner has left the organization.
 * The skill keeps its address, versions, history and pending reviews; only the owner changes.
 * An active promoted global copy must keep the same owner as its source, so it moves along.
 */
@Service
public class SkillOwnershipService {
    private final SkillRepository skills;
    private final NamespaceRepository namespaces;
    private final NamespaceMemberRepository members;
    private final UserAccountRepository users;
    private final PromotionRequestRepository promotions;
    private final ApplicationEventPublisher events;

    public SkillOwnershipService(SkillRepository skills, NamespaceRepository namespaces,
            NamespaceMemberRepository members, UserAccountRepository users,
            PromotionRequestRepository promotions, ApplicationEventPublisher events) {
        this.skills = skills;
        this.namespaces = namespaces;
        this.members = members;
        this.users = users;
        this.promotions = promotions;
        this.events = events;
    }

    /** Returns the transferred skill, followed by its promoted global copy when that moved too. */
    @Transactional
    public List<Skill> transfer(Long namespaceId, String slug, String currentOwnerId, String newOwnerId,
            String actor, Map<Long, NamespaceRole> roles, Set<String> platformRoles) {
        if (roles.get(namespaceId) != NamespaceRole.OWNER && !platformRoles.contains("SUPER_ADMIN")) {
            throw new DomainForbiddenException("error.skill.owner.transfer.noPermission");
        }
        Namespace namespace = namespaces.findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceId));
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("error.skill.owner.transfer.namespaceInactive");
        }
        Skill skill = skills.findByNamespaceIdAndSlugAndOwnerId(namespaceId, slug, currentOwnerId)
                .flatMap(found -> skills.findLockedById(found.getId()))
                .filter(locked -> currentOwnerId.equals(locked.getOwnerId()))
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", slug));
        if (skill.isGithubSyncManaged() || users.findById(currentOwnerId).filter(UserAccount::isSystemAccount).isPresent()) {
            throw new DomainBadRequestException("error.skill.owner.transfer.systemOwned");
        }
        if (skill.getSourceSkillId() != null && promotions.findActiveInitialBySourceSkillId(skill.getSourceSkillId())
                .filter(initial -> skill.getId().equals(initial.getTargetSkillId())).isPresent()) {
            throw new DomainBadRequestException("error.skill.owner.transfer.promotedCopy");
        }
        boolean eligible = users.findById(newOwnerId)
                .filter(UserAccount::isActive)
                .filter(user -> !user.isSystemAccount())
                .isPresent()
                && members.findByNamespaceIdAndUserId(namespaceId, newOwnerId).isPresent();
        if (!eligible) {
            throw new DomainBadRequestException("error.skill.owner.transfer.targetIneligible");
        }
        List<Skill> moved = new ArrayList<>(List.of(skill));
        promotedCopy(skill).ifPresent(moved::add);
        for (Skill item : moved) {
            if (skills.findByNamespaceIdAndSlugAndOwnerId(item.getNamespaceId(), item.getSlug(), newOwnerId).isPresent()) {
                throw new DomainConflictException("error.skill.owner.transfer.slugTaken", item.getSlug());
            }
            item.setOwnerId(newOwnerId);
            item.setUpdatedBy(actor);
        }
        try {
            moved.forEach(skills::save);
            // save() may defer the unique (namespace, slug, owner) check until commit.
            skills.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DomainConflictException("error.skill.owner.transfer.slugTaken", slug);
        }
        moved.forEach(item -> events.publishEvent(new SkillOwnerChangedEvent(item.getId())));
        return moved;
    }

    /** The global copy created by an active promotion, matched the same way PromotionService does. */
    private Optional<Skill> promotedCopy(Skill source) {
        return promotions.findActiveInitialBySourceSkillId(source.getId())
                .map(PromotionRequest::getTargetSkillId)
                .flatMap(skills::findLockedById)
                .filter(target -> source.getId().equals(target.getSourceSkillId())
                        && source.getOwnerId().equals(target.getOwnerId()));
    }
}
