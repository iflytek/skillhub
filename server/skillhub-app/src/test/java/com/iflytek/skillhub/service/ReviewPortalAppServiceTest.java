package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
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
                suiteLifecycleService);
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
