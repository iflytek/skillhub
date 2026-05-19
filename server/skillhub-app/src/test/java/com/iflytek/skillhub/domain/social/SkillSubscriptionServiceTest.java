package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.social.event.SkillSubscribedEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnsubscribedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillSubscriptionServiceTest {

    @Mock private SkillSubscriptionRepository subscriptionRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SkillSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SkillSubscriptionService(subscriptionRepository, skillRepository, eventPublisher);
    }

    @Test
    void subscribe_createsSubscriptionAndPublishesEvent() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.subscribe(1L, "user-1");

        verify(subscriptionRepository).save(any(SkillSubscription.class));
        verify(skillRepository).incrementSubscriptionCount(1L);
        ArgumentCaptor<SkillSubscribedEvent> captor = ArgumentCaptor.forClass(SkillSubscribedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().skillId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
    }

    @Test
    void subscribe_idempotent_doesNotDuplicate() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(mock(SkillSubscription.class)));

        service.subscribe(1L, "user-1");

        verify(subscriptionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unsubscribe_deletesSubscriptionAndPublishesEvent() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        SkillSubscription existing = mock(SkillSubscription.class);
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.of(existing));

        service.unsubscribe(1L, "user-1");

        verify(subscriptionRepository).delete(existing);
        verify(skillRepository).decrementSubscriptionCount(1L);
        ArgumentCaptor<SkillUnsubscribedEvent> captor = ArgumentCaptor.forClass(SkillUnsubscribedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().skillId()).isEqualTo(1L);
    }

    @Test
    void unsubscribe_noOp_whenNotSubscribed() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());

        service.unsubscribe(1L, "user-1");

        verify(subscriptionRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void isSubscribed_returnsTrue_whenSubscriptionExists() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(mock(SkillSubscription.class)));

        assertThat(service.isSubscribed(1L, "user-1")).isTrue();
    }

    @Test
    void isSubscribed_returnsFalse_whenNoSubscription() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());

        assertThat(service.isSubscribed(1L, "user-1")).isFalse();
    }
}
