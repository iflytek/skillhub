package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteActionContext;
import com.iflytek.skillhub.domain.suite.SkillSuiteLifecycleService;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import com.iflytek.skillhub.repository.ReviewProgressQueryRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewPortalAppServiceTest {

    @Mock private com.iflytek.skillhub.repository.ReviewQueueQueryRepository reviewQueueQueryRepository;
    @Mock private com.iflytek.skillhub.domain.review.ReviewPermissionChecker reviewPermissionChecker;
    @Mock private ReviewService reviewService;
    @Mock private ReviewTaskRepository reviewTaskRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private GovernanceQueryRepository governanceQueryRepository;
    @Mock private ReviewProgressQueryRepository reviewProgressQueryRepository;
    @Mock private RbacService rbacService;
    @Mock private AuditLogService auditLogService;
    @Mock private RequestIdAccessor requestIdAccessor;
    @Mock private SkillSuiteLifecycleService suiteLifecycleService;

    private ReviewPortalAppService service;

    @BeforeEach
    void setUp() {
        service = new ReviewPortalAppService(
                reviewService,
                reviewTaskRepository,
                namespaceRepository,
                governanceQueryRepository,
                reviewProgressQueryRepository,
                rbacService,
                auditLogService,
                requestIdAccessor,
                suiteLifecycleService,
                reviewQueueQueryRepository,
                reviewPermissionChecker);
    }

    @Test
    void approveReviewDispatchesSuiteSubjectsToSuiteLifecycle() {
        ReviewTask task = suiteTask(91L);
        ReviewTaskResponse response = response(91L);
        when(reviewTaskRepository.findById(91L)).thenReturn(Optional.of(task));
        when(rbacService.getUserRoleCodes("reviewer")).thenReturn(Set.of("SKILL_ADMIN"));
        when(requestIdAccessor.current()).thenReturn("request-1");
        when(suiteLifecycleService.approveReview(
                org.mockito.ArgumentMatchers.eq(91L),
                org.mockito.ArgumentMatchers.eq("looks good"),
                org.mockito.ArgumentMatchers.any(SkillSuiteActionContext.class)))
                .thenReturn(task);
        when(governanceQueryRepository.getReviewTaskResponse(task)).thenReturn(response);

        ReviewTaskResponse actual = service.approveReview(
                91L, "looks good", "reviewer", Map.of(7L, NamespaceRole.ADMIN),
                new AuditRequestContext("127.0.0.1", "test"));

        assertThat(actual).isSameAs(response);
        ArgumentCaptor<SkillSuiteActionContext> context = ArgumentCaptor.forClass(SkillSuiteActionContext.class);
        verify(suiteLifecycleService).approveReview(
                org.mockito.ArgumentMatchers.eq(91L),
                org.mockito.ArgumentMatchers.eq("looks good"),
                context.capture());
        assertThat(context.getValue().requestId()).isEqualTo("request-1");
        assertThat(context.getValue().platformRoles()).containsExactly("SKILL_ADMIN");
        verify(reviewService, never()).approveReview(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void withdrawReviewDispatchesSuiteSubjectsWithoutSkillVersionAccess() {
        ReviewTask task = suiteTask(92L);
        when(reviewTaskRepository.findById(92L)).thenReturn(Optional.of(task));
        when(rbacService.getUserRoleCodes("author")).thenReturn(Set.of());

        service.withdrawReview(92L, "author", Map.of(7L, NamespaceRole.MEMBER), null);

        verify(suiteLifecycleService).withdrawReview(
                org.mockito.ArgumentMatchers.eq(92L),
                org.mockito.ArgumentMatchers.any(SkillSuiteActionContext.class));
        verify(reviewService, never()).withdrawReview(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void userAdminDoesNotReceiveSkillReviewQueuePermission() {
        when(rbacService.getUserRoleCodes("user-admin")).thenReturn(Set.of("USER_ADMIN"));
        assertThatThrownBy(() -> service.listReviewQueue("PENDING", null, "", null, null,
                0, 20, "DESC", "user-admin", Map.of()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class);
        verify(reviewQueueQueryRepository, never()).list(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anySet(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void teamQueuePassesOnlyOwnerAndAdminScopesToDatabaseFiltering() {
        when(rbacService.getUserRoleCodes("team-admin")).thenReturn(Set.of("USER_ADMIN"));
        when(reviewQueueQueryRepository.list(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(Set.of(7L, 9L)),
                org.mockito.ArgumentMatchers.any())).thenReturn(org.springframework.data.domain.Page.empty());
        when(governanceQueryRepository.getReviewTaskResponses(java.util.List.of())).thenReturn(java.util.List.of());
        service.listReviewQueue("ALL", null, "name", "ALL", "ALL", 0, 20, "ASC", "team-admin",
                Map.of(7L, NamespaceRole.OWNER, 9L, NamespaceRole.ADMIN, 11L, NamespaceRole.MEMBER));
        verify(reviewQueueQueryRepository).list(
                new com.iflytek.skillhub.repository.ReviewQueueQueryRepository.Filter(null, null, "name", null, null),
                false, Set.of(7L, 9L), org.springframework.data.domain.PageRequest.of(0, 20,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "submittedAt")
                                .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"))));
    }

    @Test
    void readableOwnTaskUsesDomainPermissionForApprovalControls() {
        ReviewTask task = suiteTask(91L);
        var namespace = new com.iflytek.skillhub.domain.namespace.Namespace("team", "Team", "owner");
        when(reviewTaskRepository.findById(91L)).thenReturn(Optional.of(task));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(rbacService.getUserRoleCodes("author")).thenReturn(Set.of("SKILL_ADMIN"));
        when(reviewService.canViewReview(task, "author", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN")))
                .thenReturn(true);
        when(governanceQueryRepository.getReviewTaskResponse(task)).thenReturn(response(91L));
        when(reviewPermissionChecker.canReview(task, "author", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN")))
                .thenReturn(false);
        assertThat(service.getReviewDetail(91L, "author", Map.of()).canReview()).isFalse();
        when(reviewPermissionChecker.canReview(task, "author", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN")))
                .thenReturn(true);
        assertThat(service.getReviewDetail(91L, "author", Map.of()).canReview()).isTrue();
    }

    @Test
    void platformReviewScopesIncludeNamespacesWithoutMembershipAndAreSorted() {
        when(rbacService.getUserRoleCodes("platform")).thenReturn(Set.of("SKILL_ADMIN"));
        var team = new com.iflytek.skillhub.domain.namespace.Namespace("z-team", "Team", "owner");
        var global = new com.iflytek.skillhub.domain.namespace.Namespace("global", "Global", "owner");
        global.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.GLOBAL);
        when(namespaceRepository.findAll()).thenReturn(java.util.List.of(team, global));
        assertThat(service.listReviewScopes("platform", Map.of())).extracting("slug")
                .containsExactly("global", "z-team");
    }

    @Test
    void teamReviewScopesExcludeNonTeamsAndUserAdminDoesNotGainAccess() {
        when(rbacService.getUserRoleCodes("team")).thenReturn(Set.of("USER_ADMIN"));
        var team = new com.iflytek.skillhub.domain.namespace.Namespace("team", "Team", "owner");
        team.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.TEAM);
        var catalog = new com.iflytek.skillhub.domain.namespace.Namespace("catalog", "Catalog", "owner");
        catalog.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.CATALOG);
        when(namespaceRepository.findByIdIn(org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of(team, catalog));
        assertThat(service.listReviewScopes("team", Map.of(7L, NamespaceRole.OWNER, 9L, NamespaceRole.ADMIN)))
                .extracting("slug").containsExactly("team");
        assertThatThrownBy(() -> service.listReviewScopes("team", Map.of()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class);
    }

    private ReviewTask suiteTask(Long id) {
        ReviewTask task = ReviewTask.forSuiteVersion(31L, 21L, 7L, "1.0.0", "author");
        setField(task, "id", id);
        return task;
    }

    private ReviewTaskResponse response(Long id) {
        return new ReviewTaskResponse(
                id, null, "team", null, "1.0.0", "PENDING", "author", "Author",
                null, null, null, null, null,
                "SUITE_VERSION", 21L, 31L, "starter-pack");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
