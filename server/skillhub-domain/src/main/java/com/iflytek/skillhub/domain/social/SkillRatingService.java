package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Transactional
    public SkillRating upsertReview(Long skillId, String userId, short score, String reviewText) {
        ensureSkillExists(skillId);
        SkillRating rating = ratingRepository.findBySkillIdAndUserId(skillId, userId)
                .orElseGet(() -> new SkillRating(skillId, userId, score));
        rating.updateReview(score, reviewText);
        SkillRating saved;
        try {
            saved = ratingRepository.save(rating);
            ratingRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DomainConflictException("error.request.conflict");
        }
        eventPublisher.publishEvent(new SkillRatedEvent(skillId, userId, score));
        return saved;
    }

    @Transactional
    public SkillRating clearReview(Long skillId, String userId) {
        ensureSkillExists(skillId);
        SkillRating rating = ratingRepository.findBySkillIdAndUserId(skillId, userId)
                .filter(SkillRating::hasReview)
                .orElseThrow(() -> new DomainNotFoundException("error.skillReview.notFound"));
        rating.clearReview();
        SkillRating saved = ratingRepository.save(rating);
        ratingRepository.flush();
        return saved;
    }

    @Transactional
    public SkillRating hideReview(Long reviewId, String moderatorId, String reason) {
        SkillRating rating = findReview(reviewId);
        rating.hideReview(moderatorId, reason);
        SkillRating saved = ratingRepository.save(rating);
        ratingRepository.flush();
        return saved;
    }

    @Transactional
    public SkillRating restoreReview(Long reviewId, String moderatorId) {
        SkillRating rating = findReview(reviewId);
        rating.restoreReview(moderatorId);
        SkillRating saved = ratingRepository.save(rating);
        ratingRepository.flush();
        return saved;
    }

    public Optional<Short> getUserRating(Long skillId, String userId) {
        ensureSkillExists(skillId);
        return ratingRepository.findBySkillIdAndUserId(skillId, userId)
            .map(SkillRating::getScore);
    }

    public Optional<SkillRating> getUserReview(Long skillId, String userId) {
        ensureSkillExists(skillId);
        return ratingRepository.findBySkillIdAndUserId(skillId, userId)
                .filter(SkillRating::hasReview);
    }

    public Optional<SkillRating> getUserFeedback(Long skillId, String userId) {
        ensureSkillExists(skillId);
        return ratingRepository.findBySkillIdAndUserId(skillId, userId);
    }

    private SkillRating findReview(Long reviewId) {
        return ratingRepository.findById(reviewId)
                .filter(SkillRating::hasReview)
                .orElseThrow(() -> new DomainNotFoundException("error.skillReview.notFound"));
    }

    private void ensureSkillExists(Long skillId) {
        if (skillRepository.findById(skillId).isEmpty()) {
            throw new DomainNotFoundException("skill.not_found", skillId);
        }
    }
}
