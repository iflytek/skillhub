package com.iflytek.skillhub.domain.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-18T10:00:00Z"), ZoneOffset.UTC);

    @Mock private ReviewTaskRepository reviewTaskRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private ReviewPermissionChecker permissionChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SkillGovernanceService skillGovernanceService;
    @Mock private GovernanceNotificationService governanceNotificationService;

    private ReviewService reviewService;

    private static final Long SKILL_VERSION_ID = 10L;
    private static final Long NAMESPACE_ID = 20L;
    private static final String USER_ID = "user-100";
    private static final String REVIEWER_ID = "user-200";
    private static final Long REVIEW_TASK_ID = 1L;
    private static final Long SKILL_ID = 30L;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        reviewService = new ReviewService(
                reviewTaskRepository, skillVersionRepository, skillRepository,
                namespaceRepository, permissionChecker, eventPublisher, objectMapper, skillGovernanceService, governanceNotificationService, CLOCK);
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
            Namespace namespace = createTeamNamespace();
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
            when(permissionChecker.canSubmitReview(
                    NAMESPACE_ID,
                    Map.of(NAMESPACE_ID, NamespaceRole.MEMBER))).thenReturn(true);
            ReviewTask savedTask = createPendingReviewTask();
            when(reviewTaskRepository.save(any(ReviewTask.class))).thenReturn(savedTask);

            ReviewTask result = reviewService.submitReview(
                    SKILL_VERSION_ID,
                    USER_ID,
                    Map.of(NAMESPACE_ID, NamespaceRole.MEMBER)
            );

            assertNotNull(result);
            assertEquals(SkillVersionStatus.PENDING_REVIEW, sv.getStatus());
            verify(skillVersionRepository).save(sv);
            verify(reviewTaskRepository).save(any(ReviewTask.class));
        }

        @Test
        void shouldThrowWhenSkillVersionNotFound() {
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenStatusNotDraft() {
            SkillVersion sv = createPendingReviewSkillVersion();
            Namespace namespace = createTeamNamespace();
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(createSkill()));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of(NAMESPACE_ID, NamespaceRole.MEMBER)));
        }

        @Test
        void shouldThrowOnDuplicateSubmission() {
            SkillVersion sv = createDraftSkillVersion();
            Skill skill = createSkill();
            Namespace namespace = createTeamNamespace();
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
            when(permissionChecker.canSubmitReview(
                    NAMESPACE_ID,
                    Map.of(NAMESPACE_ID, NamespaceRole.MEMBER))).thenReturn(true);
            when(reviewTaskRepository.save(any(ReviewTask.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.submitReview(
                            SKILL_VERSION_ID,
                            USER_ID,
                            Map.of(NAMESPACE_ID, NamespaceRole.MEMBER)
                    ));
        }

        @Test
        void shouldThrowWhenSubmitterLacksNamespaceMembership() {
            SkillVersion sv = createDraftSkillVersion();
            Skill skill = createSkill();
            Namespace namespace = createTeamNamespace();
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
            when(permissionChecker.canSubmitReview(NAMESPACE_ID, Map.of())).thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of()));
            verify(reviewTaskRepository, never()).save(any(ReviewTask.class));
        }

        @Test
        void shouldRejectSubmitWhenNamespaceFrozen() {
            SkillVersion sv = createDraftSkillVersion();
            Skill skill = createSkill();
            Namespace namespace = createTeamNamespace();
            namespace.setStatus(NamespaceStatus.FROZEN);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.submitReview(SKILL_VERSION_ID, USER_ID, Map.of(NAMESPACE_ID, NamespaceRole.MEMBER)));
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
            skill.setDisplayName("Published Name");
            skill.setSummary("Published Summary");
            skill.setUpdatedBy("previous-reviewer");
            assertDoesNotThrow(() -> sv.setParsedMetadataJson(objectMapper.writeValueAsString(
                    new SkillMetadata("Approved Name", "Approved Summary", "1.0.0", "Body", Map.of())
            )));

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(eq(task), eq(REVIEWER_ID), eq(ns.getType()), anyMap(), anySet()))
                    .thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(
                    REVIEW_TASK_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "LGTM", task.getVersion()))
                    .thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(skillRepository.findByNamespaceIdAndSlug(NAMESPACE_ID, "my-skill")).thenReturn(List.of(skill));
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            ReviewTask result = reviewService.approveReview(
                    REVIEW_TASK_ID, REVIEWER_ID, "LGTM",
                    Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of());

            assertNotNull(result);
            assertEquals(SkillVersionStatus.PUBLISHED, sv.getStatus());
            assertEquals(Instant.now(CLOCK), sv.getPublishedAt());
            assertEquals(SKILL_VERSION_ID, skill.getLatestVersionId());
            assertEquals("Approved Name", skill.getDisplayName());
            assertEquals("Approved Summary", skill.getSummary());
            assertEquals(REVIEWER_ID, skill.getUpdatedBy());
            verify(eventPublisher).publishEvent(any(SkillPublishedEvent.class));
            verify(governanceNotificationService).notifyUser(eq(USER_ID), eq("REVIEW"), eq("REVIEW_TASK"), eq(REVIEW_TASK_ID), eq("Review approved"), any());
        }

        @Test
        void shouldApplyRequestedVisibilityOnlyWhenReviewIsApproved() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();
            skill.setVisibility(SkillVisibility.PUBLIC);
            sv.setRequestedVisibility(SkillVisibility.PRIVATE);

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(eq(task), eq(REVIEWER_ID), eq(ns.getType()), anyMap(), anySet()))
                    .thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(
                    REVIEW_TASK_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "LGTM", task.getVersion()))
                    .thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(skillRepository.findByNamespaceIdAndSlug(NAMESPACE_ID, "my-skill")).thenReturn(List.of(skill));
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            reviewService.approveReview(
                    REVIEW_TASK_ID, REVIEWER_ID, "LGTM",
                    Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of());

            assertEquals(SkillVisibility.PRIVATE, skill.getVisibility());
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
            when(skillRepository.findByNamespaceIdAndSlug(NAMESPACE_ID, "my-skill")).thenReturn(List.of(skill));
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

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
        void shouldNotifySubmitterWhenRejected() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(eq(task), eq(REVIEWER_ID), eq(ns.getType()), anyMap(), anySet()))
                    .thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(
                    REVIEW_TASK_ID, ReviewTaskStatus.REJECTED, REVIEWER_ID, "Needs work", task.getVersion()))
                    .thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(createSkill()));
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            reviewService.rejectReview(REVIEW_TASK_ID, REVIEWER_ID, "Needs work",
                    Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of());

            verify(governanceNotificationService).notifyUser(eq(USER_ID), eq("REVIEW"), eq("REVIEW_TASK"), eq(REVIEW_TASK_ID), eq("Review rejected"), any());
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
        void shouldRejectApproveWhenSkillVersionWasWithdrawnBackToDraft() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();
            sv.setStatus(SkillVersionStatus.DRAFT);

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(any(), any(), any(), any(), any())).thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.approveReview(REVIEW_TASK_ID, REVIEWER_ID, "ok",
                            Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of()));
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
        void shouldRejectApproveWhenNamespaceFrozen() {
            ReviewTask task = createPendingReviewTask();
            Namespace namespace = createTeamNamespace();
            namespace.setStatus(NamespaceStatus.FROZEN);
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.approveReview(REVIEW_TASK_ID, REVIEWER_ID, "ok", Map.of(), Set.of()));
        }

        @Test
        void superAdminCanApproveOwnSubmission() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(eq(task), eq(USER_ID), eq(ns.getType()), anyMap(), eq(Set.of("SUPER_ADMIN"))))
                    .thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(
                    REVIEW_TASK_ID, ReviewTaskStatus.APPROVED, USER_ID, "self approved", task.getVersion()))
                    .thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(skillRepository.findByNamespaceIdAndSlug(NAMESPACE_ID, "my-skill")).thenReturn(List.of(skill));
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            ReviewTask result = reviewService.approveReview(
                    REVIEW_TASK_ID, USER_ID, "self approved", Map.of(), Set.of("SUPER_ADMIN"));

            assertNotNull(result);
            assertEquals(SkillVersionStatus.PUBLISHED, sv.getStatus());
            assertEquals(USER_ID, skill.getUpdatedBy());
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

        @Test
        void shouldRejectApproveWhenOtherOwnerHasPublishedSameSlug() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill(); // owned by USER_ID

            // Another owner's skill with same slug that has a published version
            Skill otherSkill = new Skill(NAMESPACE_ID, "my-skill", "other-user", SkillVisibility.PUBLIC);
            setField(otherSkill, "id", 99L);
            SkillVersion otherPublished = new SkillVersion(99L, "1.0.0", "other-user");
            otherPublished.setStatus(SkillVersionStatus.PUBLISHED);

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(any(), any(), any(), anyMap(), anySet())).thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(any(), any(), any(), any(), any())).thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(skillRepository.findByNamespaceIdAndSlug(NAMESPACE_ID, "my-skill")).thenReturn(List.of(skill, otherSkill));
            when(skillVersionRepository.findBySkillIdAndStatus(99L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(otherPublished));

            assertThrows(DomainBadRequestException.class,
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
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(createSkill()));
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            ReviewTask result = reviewService.rejectReview(
                    REVIEW_TASK_ID, REVIEWER_ID, "needs work",
                    Map.of(NAMESPACE_ID, NamespaceRole.ADMIN), Set.of());

            assertNotNull(result);
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
        void superAdminCanRejectOwnSubmission() {
            ReviewTask task = createPendingReviewTask();
            Namespace ns = createTeamNamespace();
            SkillVersion sv = createPendingReviewSkillVersion();

            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(ns));
            when(permissionChecker.canReview(eq(task), eq(USER_ID), eq(ns.getType()), anyMap(), eq(Set.of("SUPER_ADMIN"))))
                    .thenReturn(true);
            when(reviewTaskRepository.updateStatusWithVersion(
                    REVIEW_TASK_ID, ReviewTaskStatus.REJECTED, USER_ID, "self rejected", task.getVersion()))
                    .thenReturn(1);
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(createSkill()));
            when(reviewTaskRepository.findById(REVIEW_TASK_ID)).thenReturn(Optional.of(task));

            ReviewTask result = reviewService.rejectReview(
                    REVIEW_TASK_ID, USER_ID, "self rejected", Map.of(), Set.of("SUPER_ADMIN"));

            assertNotNull(result);
            assertEquals(SkillVersionStatus.REJECTED, sv.getStatus());
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
            Skill skill = createSkill();
            Namespace namespace = createTeamNamespace();
            SkillVersion withdrawn = createDraftSkillVersion();

            when(reviewTaskRepository.findBySkillVersionIdAndStatus(SKILL_VERSION_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(task));
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
            when(skillGovernanceService.withdrawPendingVersion(skill, sv, USER_ID)).thenReturn(withdrawn);

            SkillVersion result = reviewService.withdrawReview(SKILL_VERSION_ID, USER_ID);

            assertEquals(SkillVersionStatus.DRAFT, result.getStatus());
            verify(reviewTaskRepository).delete(task);
            verify(skillGovernanceService).withdrawPendingVersion(skill, sv, USER_ID);
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

        @Test
        void shouldRejectWithdrawWhenNamespaceArchived() {
            ReviewTask task = createPendingReviewTask();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();
            Namespace namespace = createTeamNamespace();
            namespace.setStatus(NamespaceStatus.ARCHIVED);

            when(reviewTaskRepository.findBySkillVersionIdAndStatus(SKILL_VERSION_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(task));
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));

            assertThrows(DomainBadRequestException.class,
                    () -> reviewService.withdrawReview(SKILL_VERSION_ID, USER_ID));
        }

        @Test
        void shouldReturnDraftVersionWhenOnlyPendingVersionExists() {
            ReviewTask task = createPendingReviewTask();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();
            Namespace namespace = createTeamNamespace();
            SkillVersion withdrawn = createDraftSkillVersion();

            when(reviewTaskRepository.findBySkillVersionIdAndStatus(SKILL_VERSION_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(task));
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
            when(skillGovernanceService.withdrawPendingVersion(skill, sv, USER_ID)).thenReturn(withdrawn);

            SkillVersion result = reviewService.withdrawReview(SKILL_VERSION_ID, USER_ID);

            assertEquals(SkillVersionStatus.DRAFT, result.getStatus());
            verify(reviewTaskRepository).delete(task);
            verify(skillGovernanceService).withdrawPendingVersion(skill, sv, USER_ID);
        }

        @Test
        void shouldWithdrawPendingVersionAndKeepSkillWhenPublishedHistoryExists() {
            ReviewTask task = createPendingReviewTask();
            SkillVersion sv = createPendingReviewSkillVersion();
            Skill skill = createSkill();
            Namespace namespace = createTeamNamespace();
            setField(skill, "latestVersionId", 99L);
            SkillVersion withdrawn = createDraftSkillVersion();

            when(reviewTaskRepository.findBySkillVersionIdAndStatus(SKILL_VERSION_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(task));
            when(skillVersionRepository.findById(SKILL_VERSION_ID)).thenReturn(Optional.of(sv));
            when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
            when(skillGovernanceService.withdrawPendingVersion(skill, sv, USER_ID)).thenReturn(withdrawn);

            SkillVersion result = reviewService.withdrawReview(SKILL_VERSION_ID, USER_ID);

            assertEquals(SkillVersionStatus.DRAFT, result.getStatus());
            verify(reviewTaskRepository).delete(task);
            verify(skillGovernanceService).withdrawPendingVersion(skill, sv, USER_ID);
        }
    }
}
