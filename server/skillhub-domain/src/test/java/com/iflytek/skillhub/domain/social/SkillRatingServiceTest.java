package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRatingServiceTest {
    @Mock SkillRatingRepository ratingRepository;
    @Mock SkillRepository skillRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks SkillRatingService service;

    private Skill skill() {
        return new Skill(1L, "skill-1", "owner-1", SkillVisibility.PUBLIC);
    }

    @Test
    void rate_creates_new_rating() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(ratingRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rate(1L, "10", (short) 4);

        verify(ratingRepository).save(argThat(r -> r.getScore() == 4));
        verify(eventPublisher).publishEvent(any(SkillRatedEvent.class));
    }

    @Test
    void rate_updates_existing_rating() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        SkillRating existing = new SkillRating(1L, "10", (short) 3);
        when(ratingRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.of(existing));
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rate(1L, "10", (short) 5);

        assertThat(existing.getScore()).isEqualTo((short) 5);
        verify(ratingRepository).save(existing);
        verify(eventPublisher).publishEvent(any(SkillRatedEvent.class));
    }

    @Test
    void rate_invalid_score_throws() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        assertThatThrownBy(() -> service.rate(1L, "10", (short) 0))
            .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.rate(1L, "10", (short) 6))
            .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void getUserRating_returns_score() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        SkillRating existing = new SkillRating(1L, "10", (short) 4);
        when(ratingRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.of(existing));
        assertThat(service.getUserRating(1L, "10")).hasValue((short) 4);
    }

    @Test
    void getUserRating_throws_when_skill_missing() {
        when(skillRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUserRating(99L, "10"))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void upsertReview_creates_review_and_rating() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(ratingRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SkillRating review = service.upsertReview(1L, "user-1", (short) 5, "  Useful skill.  ");

        assertThat(review.getScore()).isEqualTo((short) 5);
        assertThat(review.getReviewText()).isEqualTo("Useful skill.");
        assertThat(review.getReviewStatus()).isEqualTo(SkillReviewStatus.VISIBLE);
        verify(eventPublisher).publishEvent(any(SkillRatedEvent.class));
    }

    @Test
    void upsertReview_preserves_hidden_status_when_author_edits() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        SkillRating existing = new SkillRating(1L, "user-1", (short) 2);
        existing.updateReview((short) 2, "Original review");
        existing.hideReview("moderator-1", "Policy violation");
        when(ratingRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.of(existing));
        when(ratingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SkillRating review = service.upsertReview(1L, "user-1", (short) 4, "Edited review");

        assertThat(review.getReviewStatus()).isEqualTo(SkillReviewStatus.HIDDEN);
        assertThat(review.getReviewText()).isEqualTo("Edited review");
    }

    @Test
    void upsertReview_rejects_blank_or_too_long_text() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(ratingRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertReview(1L, "user-1", (short) 4, "  "))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.upsertReview(1L, "user-1", (short) 4, "x".repeat(2001)))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void upsertReview_mapsConcurrentFirstInsertToConflict() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(ratingRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("duplicate rating"))
                .when(ratingRepository).flush();

        assertThatThrownBy(() -> service.upsertReview(1L, "user-1", (short) 4, "Useful"))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.request.conflict");

        verify(eventPublisher, never()).publishEvent(any(SkillRatedEvent.class));
    }

    @Test
    void clearReview_keeps_rating_row_and_score() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        SkillRating existing = new SkillRating(1L, "user-1", (short) 4);
        existing.updateReview((short) 4, "Useful skill");
        when(ratingRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.of(existing));
        when(ratingRepository.save(existing)).thenReturn(existing);

        SkillRating result = service.clearReview(1L, "user-1");

        assertThat(result.hasReview()).isFalse();
        assertThat(result.getScore()).isEqualTo((short) 4);
        assertThat(result.getReviewStatus()).isEqualTo(SkillReviewStatus.VISIBLE);
    }

    @Test
    void clearAndResubmitReview_preservesHiddenModerationState() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        SkillRating existing = new SkillRating(1L, "user-1", (short) 4);
        existing.updateReview((short) 4, "Hidden review");
        existing.hideReview("moderator-1", "Policy violation");
        when(ratingRepository.findBySkillIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(existing));
        when(ratingRepository.save(existing)).thenReturn(existing);

        service.clearReview(1L, "user-1");
        SkillRating resubmitted = service.upsertReview(1L, "user-1", (short) 5, "Rewritten review");

        assertThat(resubmitted.getReviewStatus()).isEqualTo(SkillReviewStatus.HIDDEN);
        assertThat(resubmitted.getModeratedBy()).isEqualTo("moderator-1");
        assertThat(resubmitted.getModerationReason()).isEqualTo("Policy violation");
        assertThat(resubmitted.getReviewText()).isEqualTo("Rewritten review");
        verify(ratingRepository, times(2)).flush();
    }

    @Test
    void moderator_can_hide_and_restore_review() {
        SkillRating existing = new SkillRating(1L, "user-1", (short) 4);
        existing.updateReview((short) 4, "Useful skill");
        when(ratingRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(ratingRepository.save(existing)).thenReturn(existing);

        SkillRating hidden = service.hideReview(7L, "moderator-1", "Off topic");
        assertThat(hidden.getReviewStatus()).isEqualTo(SkillReviewStatus.HIDDEN);
        assertThat(hidden.getModeratedBy()).isEqualTo("moderator-1");
        assertThat(hidden.getModerationReason()).isEqualTo("Off topic");

        SkillRating restored = service.restoreReview(7L, "moderator-2");
        assertThat(restored.getReviewStatus()).isEqualTo(SkillReviewStatus.VISIBLE);
        assertThat(restored.getModeratedBy()).isEqualTo("moderator-2");
        assertThat(restored.getModerationReason()).isNull();
    }

    @Test
    void moderationReason_acceptsFiveHundredCharactersAndRejectsFiveHundredOne() {
        SkillRating valid = new SkillRating(1L, "user-1", (short) 4);
        valid.updateReview((short) 4, "Useful skill");
        SkillRating invalid = new SkillRating(1L, "user-2", (short) 4);
        invalid.updateReview((short) 4, "Another useful skill");
        when(ratingRepository.findById(7L)).thenReturn(Optional.of(valid));
        when(ratingRepository.findById(8L)).thenReturn(Optional.of(invalid));
        when(ratingRepository.save(valid)).thenReturn(valid);

        SkillRating hidden = service.hideReview(7L, "moderator", "x".repeat(500));
        assertThat(hidden.getModerationReason()).hasSize(500);

        assertThatThrownBy(() -> service.hideReview(8L, "moderator", "x".repeat(501)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.skillReview.reason.tooLong");
        verify(ratingRepository, never()).save(invalid);
    }

    @Test
    void clearReview_throws_when_user_has_only_rating() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(ratingRepository.findBySkillIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(new SkillRating(1L, "user-1", (short) 4)));

        assertThatThrownBy(() -> service.clearReview(1L, "user-1"))
                .isInstanceOf(DomainNotFoundException.class);
    }
}
