package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillSuiteLifecycleServiceTest {

    @Mock private SkillSuiteRepository suiteRepository;
    @Mock private SkillSuiteVersionRepository versionRepository;
    @Mock private ReviewTaskRepository reviewTaskRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private SkillSuitePublicationValidator publicationValidator;
    @Mock private AuditLogService auditLogService;

    private SkillSuiteLifecycleService service;
    private SkillSuite suite;
    private SkillSuiteVersion version;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC);
        service = new SkillSuiteLifecycleService(
                suiteRepository, versionRepository, reviewTaskRepository, new ReviewPermissionChecker(), namespaceRepository,
                publicationValidator, auditLogService, clock, true);
        suite = new SkillSuite(1L, "writers", "Writers", "author");
        version = new SkillSuiteVersion(10L, "1.0.0", SkillVisibility.PRIVATE, "author");
        setId(suite, 10L);
        setId(version, 20L);
    }

    @Test
    void privateDraftPublishesDirectlyAndUpdatesLatestVersion() {
        stubLoaded();
        stubVersionAndSuiteSaves();
        service.confirmPrivatePublish(10L, 20L, context());

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.PUBLISHED);
        assertThat(version.getPublishedAt()).isEqualTo(Instant.parse("2026-09-07T08:00:00Z"));
        assertThat(suite.getLatestVersionId()).isEqualTo(20L);
        verify(publicationValidator).validate(suite, version);
        verify(auditLogService).record(
                "author", "PUBLISH_SKILL_SUITE_VERSION", "SKILL_SUITE_VERSION", 20L,
                "request-1", "127.0.0.1", "test", null);
    }

    @Test
    void publicDraftCreatesTypedReviewAndBecomesPending() {
        version.setVisibility(SkillVisibility.PUBLIC);
        stubLoaded();
        when(versionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewTaskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewTask task = service.submitForReview(10L, 20L, context());

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.PENDING_REVIEW);
        assertThat(task.getSubjectType()).isEqualTo(ReviewSubjectType.SUITE_VERSION);
        assertThat(task.getSubjectId()).isEqualTo(10L);
        assertThat(task.getSubjectVersionId()).isEqualTo(20L);
        verify(publicationValidator).validate(suite, version);
    }

    @Test
    void teamNamespaceOwnerCanApproveAndPublishSubmittedSuite() throws Exception {
        version.setVisibility(SkillVisibility.PUBLIC);
        version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        ReviewTask task = pendingReview("author");
        stubLoaded();
        stubVersionAndSuiteSaves();
        when(reviewTaskRepository.findById(30L)).thenReturn(Optional.of(task));
        when(reviewTaskRepository.updateStatusWithVersion(
                30L, ReviewTaskStatus.APPROVED, "owner", "Looks good", 1)).thenReturn(1);

        service.approveReview(30L, "Looks good", adminContext());

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.PUBLISHED);
        assertThat(suite.getLatestVersionId()).isEqualTo(20L);
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
    }

    @Test
    void approvalFailureKeepsTheReviewAndSuiteVersionPending() throws Exception {
        version.setVisibility(SkillVisibility.PUBLIC);
        version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        ReviewTask task = pendingReview("author");
        stubLoaded();
        when(reviewTaskRepository.findById(30L)).thenReturn(Optional.of(task));
        doThrow(new DomainBadRequestException("error.suite.member.unavailable"))
                .when(publicationValidator).validate(suite, version);

        assertThatThrownBy(() -> service.approveReview(30L, "Looks good", adminContext()))
                .isInstanceOf(DomainBadRequestException.class);

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.PENDING_REVIEW);
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        verify(reviewTaskRepository, never()).updateStatusWithVersion(any(), any(), any(), any(), any());
        verify(versionRepository, never()).save(any());
        verify(suiteRepository, never()).save(any());
    }

    @Test
    void rejectRequiresTheSuiteVersionToRemainPending() throws Exception {
        version.setVisibility(SkillVisibility.PUBLIC);
        ReviewTask task = pendingReview("author");
        stubLoaded();
        when(reviewTaskRepository.findById(30L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.rejectReview(30L, "Outdated task", adminContext()))
                .isInstanceOf(DomainBadRequestException.class);

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.DRAFT);
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        verify(reviewTaskRepository, never()).updateStatusWithVersion(any(), any(), any(), any(), any());
        verify(versionRepository, never()).save(any());
    }

    @Test
    void globalNamespaceReviewerCannotApproveOwnSubmission() throws Exception {
        version.setVisibility(SkillVisibility.PUBLIC);
        version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        ReviewTask task = pendingReview("owner");
        Namespace namespace = namespace(NamespaceType.GLOBAL, NamespaceStatus.ACTIVE);
        stubLoaded(namespace);
        when(reviewTaskRepository.findById(30L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.approveReview(30L, "self review", adminContext()))
                .isInstanceOf(DomainForbiddenException.class);

        verify(reviewTaskRepository, never()).updateStatusWithVersion(
                any(), any(), any(), any(), any());
    }

    @Test
    void frozenNamespaceCannotAcceptNewReviewSubmissions() {
        version.setVisibility(SkillVisibility.PUBLIC);
        stubLoaded(namespace(NamespaceType.TEAM, NamespaceStatus.FROZEN));

        assertThatThrownBy(() -> service.submitForReview(10L, 20L, context()))
                .isInstanceOf(DomainBadRequestException.class);

        verify(reviewTaskRepository, never()).save(any());
    }

    @Test
    void rolloutGateRejectsTypedReviewWritesBeforeLoadingTheSuite() {
        SkillSuiteLifecycleService disabled = new SkillSuiteLifecycleService(
                suiteRepository, versionRepository, reviewTaskRepository, new ReviewPermissionChecker(),
                namespaceRepository, publicationValidator, auditLogService,
                Clock.systemUTC(), false);

        assertThatThrownBy(() -> disabled.submitForReview(10L, 20L, context()))
                .isInstanceOf(DomainBadRequestException.class);

        verify(suiteRepository, never()).findById(any());
    }

    @Test
    void rolloutGateOmitsSubmitFromAllowedActions() {
        version.setVisibility(SkillVisibility.PUBLIC);
        SkillSuiteLifecycleService disabled = new SkillSuiteLifecycleService(
                suiteRepository, versionRepository, reviewTaskRepository, new ReviewPermissionChecker(),
                namespaceRepository, publicationValidator, auditLogService,
                Clock.systemUTC(), false);

        Set<SkillSuiteAllowedAction> actions = disabled.allowedActions(
                suite, version, namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE), context());

        assertThat(actions).containsExactly(SkillSuiteAllowedAction.EDIT);
    }

    @Test
    void yankingLatestVersionRecalculatesLatestPublishedSnapshot() throws Exception {
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.parse("2026-09-07T07:00:00Z"));
        suite.setLatestVersionId(20L);
        SkillSuiteVersion previous = new SkillSuiteVersion(
                10L, "0.9.0", "Previous", "Previous summary", SkillVisibility.PUBLIC, "author");
        setId(previous, 19L);
        previous.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        previous.setPublishedAt(Instant.parse("2026-09-06T07:00:00Z"));
        stubLoaded();
        stubVersionAndSuiteSaves();
        when(versionRepository.findBySuiteIdAndStatus(10L, SkillSuiteVersionStatus.PUBLISHED))
                .thenReturn(List.of(previous));

        service.yank(10L, 20L, "superseded", adminContext());

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.YANKED);
        assertThat(suite.getLatestVersionId()).isEqualTo(19L);
        assertThat(suite.getDisplayName()).isEqualTo("Previous");
        assertThat(suite.getSummary()).isEqualTo("Previous summary");
    }

    @Test
    void hideAndRestoreChangeOnlyTheSuiteGovernanceOverlay() {
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(suiteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.setHidden(10L, true, adminContext());
        assertThat(suite.isHidden()).isTrue();
        assertThat(suite.getHiddenAt()).isEqualTo(Instant.parse("2026-09-07T08:00:00Z"));

        service.setHidden(10L, false, adminContext());
        assertThat(suite.isHidden()).isFalse();
        assertThat(suite.getHiddenAt()).isNull();
        verify(suiteRepository, org.mockito.Mockito.times(2)).save(suite);
    }

    @Test
    void deleteRejectsSuiteWithPendingReview() {
        version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(versionRepository.findBySuiteId(10L)).thenReturn(List.of(version));

        assertThatThrownBy(() -> service.delete(10L, adminContext()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);

        verify(suiteRepository, never()).delete(any());
        verify(reviewTaskRepository, never()).deleteBySubjectTypeAndSubjectId(any(), any());
    }

    @Test
    void deleteRemovesSuiteReviewTasksBeforeHardDeletingContainer() {
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(versionRepository.findBySuiteId(10L)).thenReturn(List.of(version));

        service.delete(10L, adminContext());

        var ordered = org.mockito.Mockito.inOrder(reviewTaskRepository, suiteRepository);
        ordered.verify(reviewTaskRepository)
                .deleteBySubjectTypeAndSubjectId(ReviewSubjectType.SUITE_VERSION, 10L);
        ordered.verify(suiteRepository).delete(suite);
    }

    @Test
    void deleteDoesNotMutatePersistenceWhenAuditWriteFails() {
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(versionRepository.findBySuiteId(10L)).thenReturn(List.of(version));
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditLogService).record(any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.delete(10L, adminContext()))
                .isInstanceOf(IllegalStateException.class);

        verify(reviewTaskRepository, never()).deleteBySubjectTypeAndSubjectId(any(), any());
        verify(suiteRepository, never()).delete(any());
    }

    @Test
    void allowedActionsExposeOnlyEditableDraftCommandsToItsCreator() {
        version.setVisibility(SkillVisibility.PUBLIC);

        Set<SkillSuiteAllowedAction> actions = service.allowedActions(
                suite, version, namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE), context());

        assertThat(actions).containsExactlyInAnyOrder(
                SkillSuiteAllowedAction.EDIT,
                SkillSuiteAllowedAction.SUBMIT);
    }

    @Test
    void allowedActionsExposePublishedVersionGovernanceToNamespaceOwner() {
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        when(versionRepository.findBySuiteIdAndStatus(10L, SkillSuiteVersionStatus.PENDING_REVIEW))
                .thenReturn(List.of());

        Set<SkillSuiteAllowedAction> actions = service.allowedActions(
                suite, version, namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE), adminContext());

        assertThat(actions).containsExactlyInAnyOrder(
                SkillSuiteAllowedAction.CREATE_VERSION,
                SkillSuiteAllowedAction.YANK,
                SkillSuiteAllowedAction.HIDE,
                SkillSuiteAllowedAction.ARCHIVE,
                SkillSuiteAllowedAction.DELETE);
    }

    @Test
    void allowedActionsRespectContainerStateAndPendingReview() {
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suite.setHidden(true);
        suite.setStatus(SkillSuiteStatus.ARCHIVED);
        SkillSuiteVersion pending = new SkillSuiteVersion(
                10L, "2.0.0", SkillVisibility.PUBLIC, "author");
        pending.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        when(versionRepository.findBySuiteIdAndStatus(10L, SkillSuiteVersionStatus.PENDING_REVIEW))
                .thenReturn(List.of(pending));

        Set<SkillSuiteAllowedAction> actions = service.allowedActions(
                suite, version, namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE), adminContext());

        assertThat(actions).containsExactlyInAnyOrder(
                SkillSuiteAllowedAction.YANK,
                SkillSuiteAllowedAction.RESTORE,
                SkillSuiteAllowedAction.UNARCHIVE);
    }

    @Test
    void allowedActionsAreEmptyAfterCreatorLeavesNamespace() {
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        SkillSuiteActionContext formerCreator = new SkillSuiteActionContext(
                "author", Map.of(), Set.of(), null, null, null);

        Set<SkillSuiteAllowedAction> actions = service.allowedActions(
                suite, version, namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE), formerCreator);

        assertThat(actions).isEmpty();
    }

    @Test
    void formerCreatorCannotReopenARejectedVersionAfterLeavingTheNamespace() {
        version.setStatus(SkillSuiteVersionStatus.REJECTED);
        stubLoaded();
        SkillSuiteActionContext formerCreator = new SkillSuiteActionContext(
                "author", Map.of(), Set.of(), null, null, null);

        assertThatThrownBy(() -> service.reopenRejected(10L, 20L, formerCreator))
                .isInstanceOf(DomainForbiddenException.class);

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.REJECTED);
        verify(versionRepository, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void suiteCreatorKeepsDraftManagementWhenAnAdministratorCreatedTheVersion() throws Exception {
        version = new SkillSuiteVersion(10L, "1.0.0", SkillVisibility.PUBLIC, "delegated-admin");
        setId(version, 20L);

        Set<SkillSuiteAllowedAction> creatorActions = service.allowedActions(
                suite, version, namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE), context());
        SkillSuiteActionContext delegatedMember = new SkillSuiteActionContext(
                "delegated-admin", Map.of(1L, NamespaceRole.MEMBER), Set.of(), null, null, null);
        Set<SkillSuiteAllowedAction> delegatedActions = service.allowedActions(
                suite, version, namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE), delegatedMember);

        assertThat(creatorActions).containsExactlyInAnyOrder(
                SkillSuiteAllowedAction.EDIT,
                SkillSuiteAllowedAction.SUBMIT);
        assertThat(delegatedActions).isEmpty();
    }

    private SkillSuiteActionContext context() {
        return new SkillSuiteActionContext(
                "author", Map.of(1L, NamespaceRole.MEMBER), Set.of(),
                "request-1", "127.0.0.1", "test");
    }

    private SkillSuiteActionContext adminContext() {
        return new SkillSuiteActionContext(
                "owner", Map.of(1L, NamespaceRole.OWNER), Set.of(),
                "request-2", "127.0.0.1", "test");
    }

    private void stubLoaded() {
        stubLoaded(namespace(NamespaceType.TEAM, NamespaceStatus.ACTIVE));
    }

    private void stubLoaded(Namespace namespace) {
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(versionRepository.findById(20L)).thenReturn(Optional.of(version));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));
    }

    private void stubVersionAndSuiteSaves() {
        when(versionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(suiteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Namespace namespace(NamespaceType type, NamespaceStatus status) {
        Namespace namespace = new Namespace("team", "Team", "owner");
        namespace.setType(type);
        namespace.setStatus(status);
        return namespace;
    }

    private ReviewTask pendingReview(String submittedBy) throws Exception {
        ReviewTask task = ReviewTask.forSuiteVersion(20L, 10L, 1L, "1.0.0", submittedBy);
        setId(task, 30L);
        return task;
    }

    private void setId(Object target, Long id) throws Exception {
        var field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
