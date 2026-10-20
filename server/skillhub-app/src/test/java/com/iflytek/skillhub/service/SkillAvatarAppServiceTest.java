package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AvatarReviewSettingsResponse;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillAvatarCandidate;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.infra.jpa.SkillAvatarCandidateJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillJpaRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.storage.ObjectStorageService;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.junit.jupiter.api.AfterEach;

@ExtendWith(MockitoExtension.class)
class SkillAvatarAppServiceTest {
    @Mock private SkillJpaRepository skills;
    @Mock private NamespaceRepository namespaces;
    @Mock private SkillAvatarCandidateJpaRepository candidates;
    @Mock private ObjectStorageService storage;
    @Mock private SkillAvatarImageProcessor imageProcessor;
    @Mock private AvatarReviewSettingsService settings;
    @Mock private AvatarModerationGateway moderation;
    @Mock private VisibilityChecker visibility;
    @Mock private SkillQueryService skillQueries;
    @Mock private AuditLogService audit;
    @Mock private RequestIdAccessor requestIds;
    @Mock private EntityManager entityManager;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private com.iflytek.skillhub.domain.review.ReviewTaskRepository reviews;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    private SkillAvatarAppService service;
    private Namespace namespace;
    private Skill skill;

    @BeforeEach
    void setup() {
        service = new SkillAvatarAppService(org.mockito.Mockito.mock(com.iflytek.skillhub.auth.rbac.RbacService.class), org.mockito.Mockito.mock(com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class), skills, namespaces, candidates, storage, imageProcessor,
                settings, moderation, visibility, skillQueries, audit, requestIds, entityManager, transactionManager);
        service.configureUnifiedReviews(reviews, new com.iflytek.skillhub.domain.review.ReviewPermissionChecker());
        service.setEvents(events);
        org.mockito.Mockito.lenient().when(moderation.context(any())).thenReturn(
                new AvatarModerationProvider.Context("fixture-image", "test-v1", "fixture-policy-v1", null));
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenAnswer(ignored -> new SimpleTransactionStatus());
        namespace = new Namespace("team", "Team", "owner");
        ReflectionTestUtils.setField(namespace, "id", 10L);
        ReflectionTestUtils.setField(namespace, "type", NamespaceType.TEAM);
        skill = new Skill(10L, "foo", "owner", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 11L);
        org.mockito.Mockito.lenient().when(skills.findByNamespaceIdAndSlugAndOwnerId(10L, "foo", "owner"))
                .thenReturn(Optional.of(skill));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void scopeCandidateUsesExistingAsynchronousSubmissionEvent() {
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        var active = new SkillAvatarCandidate(11L, "owner", "avatar/private.png", "abc", SkillVisibility.PRIVATE, false, false, null);
        active.activate();
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(candidates.findById(21L)).thenReturn(Optional.of(active));
        when(storage.getObject("avatar/private.png")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(true, true, 1, null));
        when(moderation.requireConfiguredProvider()).thenReturn("xfyun");
        when(candidates.saveAndFlush(any())).thenAnswer(call -> {
            SkillAvatarCandidate candidate = call.getArgument(0);
            ReflectionTestUtils.setField(candidate, "id", 22L);
            return candidate;
        });
        var task = new com.iflytek.skillhub.domain.review.ReviewTask(99L, 11L, 10L, "1.0.1", "owner");
        TransactionSynchronizationManager.initSynchronization();
        service.attachScopeToReview(11L, SkillVisibility.PUBLIC, task, "owner", new AuditRequestContext(null, null));
        verify(events).publishEvent(new SkillAvatarUploadService.AvatarSubmittedEvent(22L, new AuditRequestContext(null, null)));
        org.mockito.Mockito.verify(moderation, org.mockito.Mockito.never()).moderate(any(), any());
        org.assertj.core.api.Assertions.assertThat(task.getAvatarCandidateId()).isEqualTo(22L);
    }

    @Test
    void privateAvatarCannotBeReadByNamespaceAdmin() {
        skill.setVisibility(SkillVisibility.PRIVATE);
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        SkillQueryService.SkillDetailDTO detail = new SkillQueryService.SkillDetailDTO(
                11L, null, null, null, null, null, null, null, null, null, null, null, null,
                false, null, null, null, false, false, false, false, null, null, null, null, null, null, null);
        when(skillQueries.getSkillDetail(eq("team"), eq("foo"), eq("admin"), any())).thenReturn(detail);
        when(((SkillRepository) skills).findById(11L)).thenReturn(Optional.of(skill));
        when(visibility.canAccess(eq(skill), eq("admin"), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.readActive("team", "foo", "admin",
                Map.of(10L, NamespaceRole.ADMIN), Set.of()))
                .isInstanceOf(DomainNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void privateCandidateCannotBeReadByNamespaceAdmin() {
        when(namespaces.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(((SkillRepository) skills).findById(11L)).thenReturn(Optional.of(skill));
        SkillAvatarCandidate candidate = new SkillAvatarCandidate(11L, "owner", "avatar/private.png", "abc",
                SkillVisibility.PRIVATE, false, false, null);
        ReflectionTestUtils.setField(candidate, "id", 21L);
        when(candidates.findById(21L)).thenReturn(Optional.of(candidate));
        assertThatThrownBy(() -> service.readCandidate("team", "foo", 21L, "admin",
                Map.of(10L, NamespaceRole.ADMIN), Set.of()))
                .isInstanceOf(DomainNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void expandingVisibilityCreatesNewCandidateWithDistinctObjectKey() {
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        skill.setLatestVersionId(4L);
        SkillAvatarCandidate old = new SkillAvatarCandidate(11L, "owner", "avatar/private.png", "abc",
                SkillVisibility.PRIVATE, false, false, null);
        old.activate();
        ReflectionTestUtils.setField(old, "id", 21L);
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(candidates.findById(21L)).thenReturn(Optional.of(old));
        when(storage.getObject("avatar/private.png")).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(false, true, 1, null));
        when(candidates.saveAndFlush(any())).thenAnswer(invocation -> {
            SkillAvatarCandidate created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 22L);
            return created;
        });
        TransactionSynchronizationManager.initSynchronization();

        var result = service.prepareScopeReview(11L, "publisher");

        org.assertj.core.api.Assertions.assertThat(result.candidateStatus()).isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(result.avatarUrl()).isNull();
        org.assertj.core.api.Assertions.assertThat(skill.getPendingAvatarId()).isEqualTo(22L);
        org.assertj.core.api.Assertions.assertThat(old.getStatus()).isEqualTo(SkillAvatarCandidate.Status.ACTIVE);
        org.mockito.ArgumentCaptor<String> key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).putObject(key.capture(), any(), eq(1L), eq("image/png"));
        org.assertj.core.api.Assertions.assertThat(key.getValue()).isNotEqualTo("avatar/private.png");
        org.mockito.ArgumentCaptor<SkillAvatarCandidate> created =
                org.mockito.ArgumentCaptor.forClass(SkillAvatarCandidate.class);
        verify(candidates).saveAndFlush(created.capture());
        org.assertj.core.api.Assertions.assertThat(created.getValue().getScopeReviewVersionId()).isEqualTo(4L);
    }

    @Test
    void expandingVisibilityKeepsExistingTargetScopeCandidate() {
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        skill.setPendingAvatarId(22L);
        SkillAvatarCandidate pending = new SkillAvatarCandidate(11L, "owner", "avatar/new.png", "abc",
                SkillVisibility.PUBLIC, false, true, null);
        ReflectionTestUtils.setField(pending, "id", 22L);
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(candidates.findById(22L)).thenReturn(Optional.of(pending));

        org.assertj.core.api.Assertions.assertThat(service.prepareScopeReview(11L, "publisher")).isNull();
        verifyNoInteractions(storage);
    }

    @Test
    void storageFailureLeavesOldAvatarHiddenAndLaterRecoveryCanCreateCandidate() {
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        SkillAvatarCandidate old = new SkillAvatarCandidate(11L, "owner", "avatar/private.png", "abc",
                SkillVisibility.PRIVATE, false, false, null);
        old.activate();
        ReflectionTestUtils.setField(old, "id", 21L);
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(candidates.findById(21L)).thenReturn(Optional.of(old));
        when(storage.getObject("avatar/private.png"))
                .thenThrow(new IllegalStateException("temporary storage outage"))
                .thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(false, true, 1, null));
        when(candidates.saveAndFlush(any())).thenAnswer(invocation -> {
            SkillAvatarCandidate created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 22L);
            return created;
        });

        assertThatThrownBy(() -> service.prepareScopeReview(11L, "publisher"))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThat(SkillAvatarUrls.current(skill, "team")).isNull();
        org.assertj.core.api.Assertions.assertThat(skill.getPendingAvatarId()).isNull();

        TransactionSynchronizationManager.initSynchronization();
        var recovered = service.prepareScopeReview(11L, "publisher");
        org.assertj.core.api.Assertions.assertThat(recovered.candidateId()).isEqualTo(22L);
        org.assertj.core.api.Assertions.assertThat(recovered.avatarUrl()).isNull();
    }

    @Test
    void machineStorageFailureBecomesRetriableErrorThenPassesOnRecovery() {
        skill.setPendingAvatarId(21L);
        SkillAvatarCandidate candidate = new SkillAvatarCandidate(11L, "owner", "avatar/new.png", "abc",
                SkillVisibility.PUBLIC, true, false, "xfyun");
        ReflectionTestUtils.setField(candidate, "id", 21L);
        when(candidates.findById(21L)).thenReturn(Optional.of(candidate));
        when(storage.getObject("avatar/new.png"))
                .thenThrow(new IllegalStateException("temporary storage outage"))
                .thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(moderation.moderate(eq("xfyun"), any(byte[].class)))
                .thenReturn(AvatarModerationProvider.Verdict.PASS);

        var failed = service.processMachine(21L, new AuditRequestContext(null, null));
        org.assertj.core.api.Assertions.assertThat(failed.machineStatus()).isEqualTo("ERROR");
        org.assertj.core.api.Assertions.assertThat(failed.avatarUrl()).isNull();
        org.assertj.core.api.Assertions.assertThat(skill.getPendingAvatarId()).isEqualTo(21L);
        assertThatThrownBy(() -> service.processMachine(21L, new AuditRequestContext(null, null)))
                .isInstanceOf(DomainConflictException.class);
        ReflectionTestUtils.setField(candidate, "machineNextRetryAt", Instant.now().minusSeconds(1));

        var recovered = service.processMachine(21L, new AuditRequestContext(null, null));
        org.assertj.core.api.Assertions.assertThat(recovered.machineStatus()).isEqualTo("PASSED");
        org.assertj.core.api.Assertions.assertThat(recovered.avatarUrl()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(skill.getPendingAvatarId()).isNull();
    }

    @Test
    void scopeRecoveryReusesPendingErroredCandidateInsteadOfCreatingAnother() {
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        skill.setPendingAvatarId(22L);
        SkillAvatarCandidate pending = new SkillAvatarCandidate(11L, "owner", "avatar/new.png", "abc",
                SkillVisibility.PUBLIC, true, false, "xfyun");
        pending.machineResult(SkillAvatarCandidate.MachineStatus.ERROR);
        ReflectionTestUtils.setField(pending, "id", 22L);
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(candidates.findById(22L)).thenReturn(Optional.of(pending));

        var result = service.prepareScopeReview(11L, "publisher");

        org.assertj.core.api.Assertions.assertThat(result.candidateId()).isEqualTo(22L);
        org.assertj.core.api.Assertions.assertThat(result.machineStatus()).isEqualTo("ERROR");
        verifyNoInteractions(storage);
    }

    @Test
    void abandonedPendingMachineCandidateIsRecoveredWithoutCreatingAnother() {
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        skill.setPendingAvatarId(22L);
        SkillAvatarCandidate pending = new SkillAvatarCandidate(11L, "owner", "avatar/new.png", "abc",
                SkillVisibility.PUBLIC, true, false, "xfyun");
        ReflectionTestUtils.setField(pending, "id", 22L);
        ReflectionTestUtils.setField(pending, "createdAt", Instant.now().minusSeconds(601));
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(candidates.findById(22L)).thenReturn(Optional.of(pending));
        when(storage.getObject("avatar/new.png")).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        when(moderation.moderate(eq("xfyun"), any(byte[].class)))
                .thenReturn(AvatarModerationProvider.Verdict.PASS);

        var abandoned = service.prepareScopeReview(11L, "publisher");
        var recovered = service.processMachine(abandoned.candidateId(), new AuditRequestContext(null, null));

        org.assertj.core.api.Assertions.assertThat(recovered.machineStatus()).isEqualTo("PASSED");
        org.assertj.core.api.Assertions.assertThat(skill.getActiveAvatarId()).isEqualTo(22L);
        org.mockito.Mockito.verify(candidates, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void lateMachineResultCannotOverwriteNewerAttempt() {
        SkillAvatarCandidate candidate = pendingCandidate();
        when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenAnswer(ignored -> {
            Instant afterLease = Instant.now().plusSeconds(601);
            org.assertj.core.api.Assertions.assertThat(candidate.claimMachineAttempt(
                    "newer-attempt", afterLease, afterLease.plusSeconds(600))).isTrue();
            return AvatarModerationProvider.Verdict.PASS;
        });

        var result = service.processMachine(21L, new AuditRequestContext(null, null));

        org.assertj.core.api.Assertions.assertThat(result.machineStatus()).isEqualTo("PROCESSING");
        org.assertj.core.api.Assertions.assertThat(skill.getActiveAvatarId()).isNull();
        org.assertj.core.api.Assertions.assertThat(skill.getPendingAvatarId()).isEqualTo(21L);
    }

    @Test
    void expiredMachineLeaseIsReclaimedAfterCrash() {
        skill.activateAvatar(21L, SkillVisibility.PRIVATE);
        skill.setPendingAvatarId(22L);
        SkillAvatarCandidate pending = new SkillAvatarCandidate(11L, "owner", "avatar/new.png", "abc",
                SkillVisibility.PUBLIC, true, false, "xfyun");
        ReflectionTestUtils.setField(pending, "id", 22L);
        Instant old = Instant.now().minusSeconds(601);
        pending.claimMachineAttempt("crashed-attempt", old, old.plusSeconds(600));
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        when(candidates.findById(22L)).thenReturn(Optional.of(pending));
        when(storage.getObject("avatar/new.png")).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        when(moderation.moderate(eq("xfyun"), any(byte[].class)))
                .thenReturn(AvatarModerationProvider.Verdict.PASS);

        var expired = service.prepareScopeReview(11L, "publisher");
        var recovered = service.processMachine(expired.candidateId(), new AuditRequestContext(null, null));

        org.assertj.core.api.Assertions.assertThat(expired.machineStatus()).isEqualTo("PROCESSING");
        org.assertj.core.api.Assertions.assertThat(recovered.machineStatus()).isEqualTo("PASSED");
        org.assertj.core.api.Assertions.assertThat(skill.getActiveAvatarId()).isEqualTo(22L);
        org.mockito.Mockito.verify(candidates, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void processingMachineCandidateDoesNotEnterHumanReviewQueue() {
        SkillAvatarCandidate pending = new SkillAvatarCandidate(11L, "owner", "avatar/new.png", "abc",
                SkillVisibility.PUBLIC, true, true, "xfyun");
        pending.claimMachineAttempt("in-flight", Instant.now(), Instant.now().plusSeconds(600));
        when(candidates.findByStatusAndReviewStatusOrderByCreatedAtAsc(
                SkillAvatarCandidate.Status.PENDING, SkillAvatarCandidate.ReviewStatus.PENDING))
                .thenReturn(List.of(pending));

        org.assertj.core.api.Assertions.assertThat(service.reviewQueue("reviewer",
                Map.of(10L, NamespaceRole.ADMIN), Set.of())).isEmpty();
        verifyNoInteractions(skills);
    }

    @Test
    void pendingVisibilityRequestCannotBeSupersededByANewAvatar() {
        when(namespaces.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(reviews.existsBySkillIdAndRequestedVisibilityIsNotNullAndStatus(11L,
                com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.submitStored("team", "foo", "owner", "new/image.png", "sha",
                SkillVisibility.PUBLIC, new AuditRequestContext(null, null)))
                .isInstanceOf(DomainConflictException.class).hasMessageContaining("error.skill.visibility.pending");
        verifyNoInteractions(candidates, storage);
    }

    @Test
    void nonOwnerCannotSubmitBeforeWritingToStorage() {
        when(namespaces.findBySlug("team")).thenReturn(Optional.of(namespace));
        assertThatThrownBy(() -> service.submit("team", "foo", "stranger", new byte[] { 1 },
                new AuditRequestContext(null, null))).isInstanceOf(DomainForbiddenException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void staleApprovalCannotReplaceNewerCandidate() {
        when(namespaces.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(((SkillRepository) skills).findById(11L)).thenReturn(Optional.of(skill));
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        SkillAvatarCandidate candidate = new SkillAvatarCandidate(11L, "owner", "avatar/old.png", "abc",
                SkillVisibility.PUBLIC, false, true, null);
        when(candidates.findById(21L)).thenReturn(Optional.of(candidate));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        assertThatThrownBy(() -> service.decide("team", "foo", 21L, true, "fine", "reviewer",
                Map.of(10L, NamespaceRole.ADMIN), Set.of(), new AuditRequestContext(null, null)))
                .isInstanceOf(DomainConflictException.class);
        org.assertj.core.api.Assertions.assertThat(candidate.getReviewStatus())
                .isEqualTo(SkillAvatarCandidate.ReviewStatus.PENDING);
    }

    @Test
    void reviewRequiresMatchingNamespaceRole() {
        when(namespaces.findBySlug("team")).thenReturn(Optional.of(namespace));
        when(((SkillRepository) skills).findById(11L)).thenReturn(Optional.of(skill));
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        SkillAvatarCandidate candidate = new SkillAvatarCandidate(11L, "owner", "avatar/old.png", "abc",
                SkillVisibility.PUBLIC, false, true, null);
        when(candidates.findById(21L)).thenReturn(Optional.of(candidate));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        assertThatThrownBy(() -> service.decide("team", "foo", 21L, true, "fine", "reviewer",
                Map.of(), Set.of(), new AuditRequestContext(null, null)))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void unknownMachineResultLeavesCandidatePrivate() {
        SkillAvatarCandidate candidate = pendingCandidate();
        when(moderation.moderate(org.mockito.ArgumentMatchers.eq("xfyun"), any(byte[].class)))
                .thenReturn(AvatarModerationProvider.Verdict.ERROR);

        var result = service.processMachine(21L, new AuditRequestContext(null, null));

        org.assertj.core.api.Assertions.assertThat(result.machineStatus()).isEqualTo("ERROR");
        org.assertj.core.api.Assertions.assertThat(result.avatarUrl()).isNull();
        org.assertj.core.api.Assertions.assertThat(skill.getPendingAvatarId()).isEqualTo(21L);
    }

    @Test
    void staleMachineResultCannotActivateSupersededCandidate() {
        SkillAvatarCandidate candidate = pendingCandidate();
        when(moderation.moderate(org.mockito.ArgumentMatchers.eq("xfyun"), any(byte[].class)))
                .thenAnswer(ignored -> {
                    candidate.supersede();
                    skill.setPendingAvatarId(22L);
                    return AvatarModerationProvider.Verdict.PASS;
                });

        var result = service.processMachine(21L, new AuditRequestContext(null, null));

        org.assertj.core.api.Assertions.assertThat(result.avatarUrl()).isNull();
        org.assertj.core.api.Assertions.assertThat(skill.getPendingAvatarId()).isEqualTo(22L);
        org.assertj.core.api.Assertions.assertThat(candidate.getStatus())
                .isEqualTo(SkillAvatarCandidate.Status.SUPERSEDED);
    }

    private SkillAvatarCandidate pendingCandidate() {
        skill.setPendingAvatarId(21L);
        SkillAvatarCandidate candidate = new SkillAvatarCandidate(11L, "owner", "avatar/one.png", "abc",
                SkillVisibility.PUBLIC, true, false, "xfyun");
        ReflectionTestUtils.setField(candidate, "id", 21L);
        when(candidates.findById(21L)).thenReturn(Optional.of(candidate));
        when(storage.getObject("avatar/one.png")).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
        when(skills.findLockedById(11L)).thenReturn(Optional.of(skill));
        when(namespaces.findById(10L)).thenReturn(Optional.of(namespace));
        return candidate;
    }

}
