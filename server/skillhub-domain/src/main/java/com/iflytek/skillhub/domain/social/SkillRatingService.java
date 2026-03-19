package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Domain service for creating or updating user ratings on skills and emitting
 * the corresponding social event.
 */
@Service
public class SkillRatingService {
    private final SkillRatingRepository ratingRepository;
    private final SkillRepository skillRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillRatingService(SkillRatingRepository ratingRepository,
                              SkillRepository skillRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.ratingRepository = ratingRepository;
        this.skillRepository = skillRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void rate(Long skillId, String userId, short score) {
        ensureSkillExists(skillId);
        if (score < 1 || score > 5) {
            throw new DomainBadRequestException("error.rating.score.invalid");
        }
        Optional<SkillRating> existing = ratingRepository.findBySkillIdAndUserId(skillId, userId);
        if (existing.isPresent()) {
            existing.get().updateScore(score);
            ratingRepository.save(existing.get());
        } else {
            ratingRepository.save(new SkillRating(skillId, userId, score));
        }
        eventPublisher.publishEvent(new SkillRatedEvent(skillId, userId, score));
    }

    public Optional<Short> getUserRating(Long skillId, String userId) {
        ensureSkillExists(skillId);
        return ratingRepository.findBySkillIdAndUserId(skillId, userId)
            .map(SkillRating::getScore);
    }

    private void ensureSkillExists(Long skillId) {
        if (skillRepository.findById(skillId).isEmpty()) {
            throw new DomainNotFoundException("skill.not_found", skillId);
        }
    }
}
