package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillStarServiceTest {
    @Mock SkillStarRepository starRepository;
    @Mock SkillRepository skillRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks SkillStarService service;

    private Skill skill() {
        return new Skill(1L, "skill-1", "owner-1", SkillVisibility.PUBLIC);
    }

    @Test
    void star_skill_creates_record_and_publishes_event() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(starRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.empty());
        when(starRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.star(1L, "10");

        verify(starRepository).save(any(SkillStar.class));
        verify(eventPublisher).publishEvent(any(SkillStarredEvent.class));
    }

    @Test
    void star_skill_already_starred_is_idempotent() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(starRepository.findBySkillIdAndUserId(1L, "10"))
            .thenReturn(Optional.of(new SkillStar(1L, "10")));

        service.star(1L, "10");

        verify(starRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unstar_skill_deletes_record_and_publishes_event() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        SkillStar existing = new SkillStar(1L, "10");
        when(starRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.of(existing));

        service.unstar(1L, "10");

        verify(starRepository).delete(existing);
        verify(eventPublisher).publishEvent(any(SkillUnstarredEvent.class));
    }

    @Test
    void unstar_skill_not_starred_is_noop() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(starRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.empty());

        service.unstar(1L, "10");

        verify(starRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void isStarred_returns_true_when_exists() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(starRepository.findBySkillIdAndUserId(1L, "10"))
            .thenReturn(Optional.of(new SkillStar(1L, "10")));
        assertThat(service.isStarred(1L, "10")).isTrue();
    }

    @Test
    void star_skill_throws_when_skill_missing() {
        when(skillRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.star(99L, "10"))
                .isInstanceOf(DomainNotFoundException.class);
    }
}
