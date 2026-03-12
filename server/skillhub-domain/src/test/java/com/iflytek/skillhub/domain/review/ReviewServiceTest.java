package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewTaskRepository reviewTaskRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private ReviewPermissionChecker permissionChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;

    private ReviewService reviewService;

    private static final Long SKILL_VERSION_ID = 10L;
    private static final Long NAMESPACE_ID = 20L;
    private static final String USER_ID = "user-100";
    private static final String REVIEWER_ID = "user-200";
    private static final Long REVIEW_TASK_ID = 1L;
    private static final Long SKILL_ID = 30L;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewTaskRepository, skillVersionRepository, skillRepository,
                namespaceRepository, permissionChecker, eventPublisher, entityManager);
    }

    private SkillVersion createDraftSkillVersion() {
        SkillVersion sv = new SkillVersion(SKILL_ID, "1.0.0", USER_ID);
        setField(sv, "id", SKILL_VERSION_ID);
        return sv;
    }

    private SkillVersion createPendingReviewSkillVersion() {
        SkillVersion sv = createDraftSkillVersion();
        sv.setStatus(SkillVersionStatus.PENDING_REVIEW);
        return sv;
    }

    private ReviewTask createPendingReviewTask() {
        ReviewTask task = new ReviewTask(SKILL_VERSION_ID, NAMESPACE_ID, USER_ID);
        setField(task, "id", REVIEW_TASK_ID);
        return task;
    }

    private Namespace createTeamNamespace() {
        Namespace ns = new Namespace("team-a", "Team A", "user-1");
        setField(ns, "id", NAMESPACE_ID);
        return ns;
    }

    private Skill createSkill() {
        Skill skill = new Skill(NAMESPACE_ID, "my-skill", USER_ID, SkillVisibility.PUBLIC);
        setField(skill, "id", SKILL_ID);
        return skill;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    class SubmitReview {

        @Test
        void shouldSubmitReviewSuccessfully() {
            SkillVersion sv = createDraftSkillVersion();
            Skill skill = createSkill();
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(permissionChecker.canSubmitForReview(eq(skill), eq(USER_ID), anyMap(), anySet())).thenReturn(true);
            ReviewTask savedTask = createPendingReviewTask();
            when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(savedTask);

            ReviewTask result = reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of(), Set.of());

            assertNotNull(result);
            assertEquals(SkillVersionStatus.PENDING_REVIEW, sv.getStatus());
            verify(skillVersionRepository).save(sv);
            verify(reviewTaskRepository).save(any(ReviewTask.class));
        }

        @Test
        void shouldThrowWhenSkillVersionNotFound() {
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of(), Set.of()));
        }

        @Test
        void shouldThrowWhenStatusNotDraft() {
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(permissionChecker.canSubmitForReview(eq(skill), eq(USER_ID), anyMap(), anySet())).thenReturn(true);

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of(), Set.of()));
        }

        @Test
        void shouldThrowOnDuplicateSubmission() {
            SkillVersion sv = createDraftSkillVersion();
            Skill skill = createSkill();
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(permissionChecker.canSubmitForReview(eq(skill), eq(USER_ID), anyMap(), anySet())).thenReturn(true);
            when(reviewTaskRepository.save(any(ReviewTask.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of(), Set.of()));
        }
    }

    @Nested
    class ApproveReview {

        @Test
        void shouldApproveReviewSuccessfully() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(eq(task), eq(REVIEWER_ID), eq(ns.getType()), anyMap(), anySet()))
                    .thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(
                    REVIEW_TASK_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "LGTM", task.getVersion()))
                    .thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));

            ReviewTask result = reviewService.approveReview(
                    REVIEW_TASK_ID, REVIEWER_ID, "LGTM",
                    Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of());

            assertNotNull(result);
            assertEquals(ReviewTaskStatus.APPROVED, result.getStatus());
            assertEquals(REVIEWER_ID, result.getReviewedBy());
            assertEquals("LGTM", result.getReviewComment());
            assertNotNull(result.getReviewedAt());
            assertEquals(SkillVersionStatus.PUBLISHED, sv.getStatus());
            assertNotNull(sv.getPublishedAt());
            assertEquals(SKILL_VERSION_ID, skill.getLatestVersionId());
            verify(eventPublisher).publishEvent(any(SkillPublishedEvent.class));
        }

        @Test
        void shouldPublishCorrectEvent() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(any(), any(), any(), any(), any())).thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));

            reviewService.approveReview(REVIEW_TASK_ID, REVIEWER_ID, "ok",
                    Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of());

            ArgumentCaptor<SkillPublishedEvent> captor = ArgumentCaptor.forClass(SkillPublishedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            SkillPublishedEvent event = captor.getValue();
            assertEquals(SKILL_ID, event.skillId());
            assertEquals(SKILL_VERSION_ID, event.versionId());
            assertEquals(REVIEWER_ID, event.publisherId());
        }

        @Test
        void shouldThrowWhenReviewTaskNotFound() {
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> reviewService.approveReview(REVIEW_TASK_ID, REVIEWER_ID, "ok", Map.of(), Set.of()));
        }

        @Test
        void shouldThrowWhenNotPending() {
            ReviewTask task = createPendingReviewTask();
            setField(task, "status", ReviewTaskStatus.APPROVED);
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.approveReview(REVIEW_TASK_ID, REVIEWER_ID, "ok", Map.of(), Set.of()));
        }

        @Test
        void shouldThrowWhenNoPermission() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> reviewService.approveReview(REVIEW_TASK_ID, REVIEWER_ID, "ok", Map.of(), Set.of()));
        }

        @Test
        void shouldThrowOnConcurrentModification() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(any(), any(), any(), any(), any())).thenReturn(0);

            assertThrows(ConcurrentModificationException.class,
                    () -> reviewService.approveReview(REVIEW_TASK_ID, REVIEWER_ID, "ok",
                            Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of()));
        }
    }

    @Nested
    class RejectReview {

        @Test
        void shouldRejectReviewSuccessfully() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(any(), any(), any(), any(), any())).thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));

            ReviewTask result = reviewService.rejectReview(
                    REVIEW_TASK_ID, REVIEWER_ID, "needs work",
                    Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of());

            assertNotNull(result);
            assertEquals(ReviewTaskStatus.REJECTED, result.getStatus());
            assertEquals(REVIEWER_ID, result.getReviewedBy());
            assertEquals("needs work", result.getReviewComment());
            assertNotNull(result.getReviewedAt());
            assertEquals(SkillVersionStatus.REJECTED, sv.getStatus());
            verify(skillVersionRepository).save(sv);
            verify(eventPublisher, never()).publishEvent(any(SkillPublishedEvent.class));
        }

        @Test
        void shouldThrowWhenNotPending() {
            ReviewTask task = createPendingReviewTask();
            setField(task, "status", ReviewTaskStatus.REJECTED);
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.rejectReview(REVIEW_TASK_ID, REVIEWER_ID, "no",
                            Map.of(), Set.of()));
        }

        @Test
        void shouldThrowWhenNoPermission() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> reviewService.rejectReview(REVIEW_TASK_ID, REVIEWER_ID, "no",
                            Map.of(), Set.of()));
        }

        @Test
        void shouldThrowOnConcurrentModification() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(any(), any(), any(), any(), any())).thenReturn(0);

            assertThrows(ConcurrentModificationException.class,
                    () -> reviewService.rejectReview(REVIEW_TASK_ID, REVIEWER_ID, "no",
                            Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of()));
        }
    }

    @Nested
    class WithdrawReview {

        @Test
        void shouldWithdrawReviewSuccessfully() {
            ReviewTask task = createPendingReviewTask();
            SkillVersion sv = createPendingReviewSkillVersion();

            when(reviewTaskRepository.findBySkillVersionIdAndStatus(SKILL_VERSION_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(task));
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));

            reviewService.withdrawReview(SKILL_VERSION_ID, USER_ID);

            verify(reviewTaskRepository).delete(task);
            assertEquals(SkillVersionStatus.DRAFT, sv.getStatus());
            verify(skillVersionRepository).save(sv);
        }

        @Test
        void shouldThrowWhenNoPendingTask() {
            when(reviewTaskRepository.findBySkillVersionIdAndStatus(SKILL_VERSION_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> reviewService.withdrawReview(SKILL_VERSION_ID, USER_ID));
        }

        @Test
        void shouldThrowWhenNotSubmitter() {
            ReviewTask task = createPendingReviewTask();
            when(reviewTaskRepository.findBySkillVersionIdAndStatus(SKILL_VERSION_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(task));

            String otherUserId = "user-999";
            assertThrows(DomainForbiddenException.class,
                    () -> reviewService.withdrawReview(SKILL_VERSION_ID, otherUserId));
        }
    }
}
