package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for starring and unstarring skills in an idempotent manner.
 */
@Service
public class SkillStarService {
    private final SkillStarRepository starRepository;
    private final SkillRepository skillRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillStarService(SkillStarRepository starRepository,
                            SkillRepository skillRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.starRepository = starRepository;
        this.skillRepository = skillRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void star(Long skillId, String userId) {
        ensureSkillExists(skillId);
        if (starRepository.findBySkillIdAndUserId(skillId, userId).isPresent()) {
            return; // idempotent
        }
        starRepository.save(new SkillStar(skillId, userId));
        eventPublisher.publishEvent(new SkillStarredEvent(skillId, userId));
    }

    @Transactional
    public void unstar(Long skillId, String userId) {
        ensureSkillExists(skillId);
        starRepository.findBySkillIdAndUserId(skillId, userId).ifPresent(star -> {
            starRepository.delete(star);
            eventPublisher.publishEvent(new SkillUnstarredEvent(skillId, userId));
        });
    }

    public boolean isStarred(Long skillId, String userId) {
        ensureSkillExists(skillId);
        return starRepository.findBySkillIdAndUserId(skillId, userId).isPresent();
    }

    private void ensureSkillExists(Long skillId) {
        if (skillRepository.findById(skillId).isEmpty()) {
            throw new DomainNotFoundException("skill.not_found", skillId);
        }
    }
}
