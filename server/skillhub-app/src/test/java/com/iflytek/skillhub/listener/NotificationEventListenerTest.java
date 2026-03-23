package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.*;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock SkillRepository skillRepository;
    @Mock SkillVersionRepository skillVersionRepository;
    @Mock NamespaceRepository namespaceRepository;
    @Mock RecipientResolver recipientResolver;
    @Mock NotificationDispatcher dispatcher;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    NotificationEventListener listener;

    private Skill mockSkill(Long id) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getNamespaceId()).thenReturn(5L);
        when(skill.getDisplayName()).thenReturn("Test Skill");
        when(skill.getSlug()).thenReturn("test-skill");
        return skill;
    }

    private void mockNamespace() {
        Namespace namespace = mock(Namespace.class);
        when(namespace.getSlug()).thenReturn("demo");
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(namespace));
    }

    @Test
    void onSkillPublished_shouldDispatchToPublisher() throws Exception {
        Skill skill = mockSkill(1L);
        when(skill.getCreatedBy()).thenReturn("publisher-1");
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onSkillPublished(new SkillPublishedEvent(1L, 10L, "publisher-1"));

        verify(dispatcher).dispatch(eq("publisher-1"), eq(NotificationCategory.PUBLISH),
                eq("SKILL_PUBLISHED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }

    @Test
    void onSkillPublished_shouldSkipWhenPublisherIsNotSkillCreator() throws Exception {
        Skill skill = mock(Skill.class);
        when(skill.getCreatedBy()).thenReturn("submitter-1");
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));

        listener.onSkillPublished(new SkillPublishedEvent(1L, 10L, "reviewer-1"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void onSkillPublished_shouldSkipWhenSkillNotFound() {
        when(skillRepository.findById(99L)).thenReturn(Optional.empty());

        listener.onSkillPublished(new SkillPublishedEvent(99L, 10L, "publisher-1"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void onReviewSubmitted_shouldDispatchToNamespaceAdmins() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(recipientResolver.resolveNamespaceAdmins(5L)).thenReturn(List.of("admin-1", "admin-2"));

        listener.onReviewSubmitted(new ReviewSubmittedEvent(100L, 1L, 10L, "submitter-1", 5L));

        verify(dispatcher, times(2)).dispatch(anyString(), eq(NotificationCategory.REVIEW),
                eq("REVIEW_SUBMITTED"), anyString(), anyString(), eq("REVIEW"), eq(100L));
        verify(dispatcher).dispatch(eq("admin-1"), any(), any(), any(), any(), any(), any());
        verify(dispatcher).dispatch(eq("admin-2"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onReviewApproved_shouldDispatchToSubmitter() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onReviewApproved(new ReviewApprovedEvent(100L, 1L, 10L, "reviewer-1", "submitter-1"));

        verify(dispatcher).dispatch(eq("submitter-1"), eq(NotificationCategory.REVIEW),
                eq("REVIEW_APPROVED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }

    @Test
    void onPromotionSubmitted_shouldDispatchToPlatformAdmins() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(recipientResolver.resolvePlatformSkillAdmins())
                .thenReturn(List.of("platform-admin-1", "super-admin-1"));

        listener.onPromotionSubmitted(new PromotionSubmittedEvent(200L, 1L, 10L, "submitter-1"));

        verify(dispatcher, times(2)).dispatch(anyString(), eq(NotificationCategory.PROMOTION),
                eq("PROMOTION_SUBMITTED"), anyString(), anyString(), eq("PROMOTION"), eq(200L));
        verify(dispatcher).dispatch(eq("platform-admin-1"), any(), any(), any(), any(), any(), any());
        verify(dispatcher).dispatch(eq("super-admin-1"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onPromotionSubmitted_shouldDispatchOncePerUniqueRecipient() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(recipientResolver.resolvePlatformSkillAdmins())
                .thenReturn(List.of("platform-admin-1", "platform-admin-1"));

        listener.onPromotionSubmitted(new PromotionSubmittedEvent(200L, 1L, 10L, "submitter-1"));

        verify(dispatcher, times(1)).dispatch(eq("platform-admin-1"), eq(NotificationCategory.PROMOTION),
                eq("PROMOTION_SUBMITTED"), anyString(), anyString(), eq("PROMOTION"), eq(200L));
    }

    @Test
    void onReportResolved_shouldDispatchToReporter() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onReportResolved(new ReportResolvedEvent(300L, 1L, "handler-1", "reporter-1", "DISMISSED"));

        verify(dispatcher).dispatch(eq("reporter-1"), eq(NotificationCategory.REPORT),
                eq("REPORT_RESOLVED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }
}
