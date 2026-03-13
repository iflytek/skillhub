package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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
}
