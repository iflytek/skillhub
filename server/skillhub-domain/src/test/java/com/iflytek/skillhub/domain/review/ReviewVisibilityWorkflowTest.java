package com.iflytek.skillhub.domain.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ReviewVisibilityWorkflowTest {
    private final ReviewTaskRepository tasks = mock(ReviewTaskRepository.class);
    private final SkillVersionRepository versions = mock(SkillVersionRepository.class);
    private final SkillRepository skills = mock(SkillRepository.class);
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final ReviewPermissionChecker permissions = mock(ReviewPermissionChecker.class);
    private final ReviewAvatarParticipant avatars = mock(ReviewAvatarParticipant.class);
    private final SkillVisibilityService visibility = mock(SkillVisibilityService.class);
    private ReviewService service;
    private ReviewTask task;

    @BeforeEach
    void setup() throws Exception {
        service = new ReviewService(org.mockito.Mockito.mock(com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class), tasks, versions, skills, namespaces, permissions,
                mock(ApplicationEventPublisher.class), new ObjectMapper(), mock(SkillGovernanceService.class),
                mock(GovernanceNotificationService.class), mock(EntityManager.class), Clock.systemUTC());
        service.setAvatarParticipant(avatars); service.setVisibilityService(visibility);
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(1L); when(skill.getNamespaceId()).thenReturn(2L);
        when(skills.findById(1L)).thenReturn(Optional.of(skill));
        when(skill.getVisibility()).thenReturn(SkillVisibility.PRIVATE); when(skill.getLatestVersionId()).thenReturn(3L);
        task = ReviewTask.forVisibility(skill, SkillVisibility.PUBLIC, "owner");
        var id = ReviewTask.class.getDeclaredField("id"); id.setAccessible(true); id.set(task, 4L);
        when(tasks.findById(4L)).thenReturn(Optional.of(task));
        when(tasks.updateStatusWithVersion(eq(4L), any(), any(), any(), any())).thenReturn(1);
        Namespace namespace = mock(Namespace.class);
        when(namespace.getStatus()).thenReturn(NamespaceStatus.ACTIVE);
        when(namespace.getType()).thenReturn(NamespaceType.TEAM);
        when(namespaces.findById(2L)).thenReturn(Optional.of(namespace));
        when(permissions.canReview(eq(task), eq("admin"), any(), anyMap(), anySet())).thenReturn(true);
    }

    @Test
    void approveParticipatesInUnifiedReviewWithoutRepublishingContent() {
        service.approveReview(4L, "admin", "checked", Map.of(), Set.of());
        verify(visibility).validateApproval(task);
        verify(visibility).approve(task, "admin");
        verify(avatars).decide(task, true, "admin", "checked");
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        verifyNoInteractions(versions);
    }

    @Test
    void rejectLeavesPublishedVersionAndScopeUntouched() {
        service.rejectReview(4L, "admin", "not ready", Map.of(), Set.of());
        verifyNoInteractions(visibility, versions);
        verify(avatars).decide(task, false, "admin", "not ready");
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.REJECTED);
    }

    @Test
    void withdrawUsesExistingReviewPathWithoutChangingPublishedContent() {
        service.withdrawMetadata(task, "owner");
        verify(skills).findById(1L);
        verify(tasks).delete(task);
        verify(avatars).withdraw(task);
        verifyNoInteractions(visibility, versions);
    }
}
