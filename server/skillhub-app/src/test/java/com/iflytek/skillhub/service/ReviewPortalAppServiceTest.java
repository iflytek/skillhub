package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ReviewPortalAppServiceTest {

    private final ReviewService reviewService = mock(ReviewService.class);
    private final ReviewTaskRepository reviewTaskRepository = mock(ReviewTaskRepository.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final GovernanceQueryRepository governanceQueryRepository = mock(GovernanceQueryRepository.class);
    private final RbacService rbacService = mock(RbacService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final ReviewPortalAppService service = new ReviewPortalAppService(
            reviewService,
            reviewTaskRepository,
            namespaceRepository,
            governanceQueryRepository,
            rbacService,
            auditLogService
    );

    @Test
    void withdrawReview_throwsWhenTaskNotFound() {
        when(reviewTaskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdrawReview(99L, "user-1", new AuditRequestContext("127.0.0.1", "JUnit")))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("review_task.not_found");
    }

    @Test
    void listReviews_withNamespaceId_namespaceNotFound_throws() {
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("SKILL_ADMIN"));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listReviews("PENDING", 7L, 0, 20, "DESC", "user-1", Map.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("namespace.not_found");
    }

    @Test
    void listReviews_withNamespaceId_noPermission_throws() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        org.springframework.test.util.ReflectionTestUtils.setField(namespace, "id", 7L);
        namespace.setType(NamespaceType.TEAM);

        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("USER"));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(reviewService.canReviewNamespace(any(), eq("user-1"), eq(NamespaceType.TEAM), anyMap(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.listReviews("PENDING", 7L, 0, 20, "DESC", "user-1", Map.of()))
                .isInstanceOf(DomainForbiddenException.class)
                .hasMessageContaining("review.no_permission");
    }

    @Test
    void listReviews_withNamespaceId_findsTasks() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        org.springframework.test.util.ReflectionTestUtils.setField(namespace, "id", 7L);
        namespace.setType(NamespaceType.TEAM);

        ReviewTask task = new ReviewTask(1L, 7L, "submitter-1");
        org.springframework.test.util.ReflectionTestUtils.setField(task, "id", 11L);

        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("USER"));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(reviewService.canReviewNamespace(any(), eq("user-1"), eq(NamespaceType.TEAM), anyMap(), any()))
                .thenReturn(true);
        when(reviewTaskRepository.findByNamespaceIdAndStatus(eq(7L), eq(ReviewTaskStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(reviewService.canViewReview(any(), eq("user-1"), eq(NamespaceType.TEAM), anyMap(), any()))
                .thenReturn(true);
        when(governanceQueryRepository.getReviewTaskResponses(any()))
                .thenReturn(List.of(new ReviewTaskResponse(11L, 1L, "team-a", "skill", "1.0.0", "PENDING", "submitter-1", null, null, null, null, null, null)));

        PageResponse<ReviewTaskResponse> response = service.listReviews("PENDING", 7L, 0, 20, "DESC", "user-1", Map.of());

        assertThat(response.items()).hasSize(1);
    }

    @Test
    void listPendingReviews_namespaceNotFound_throws() {
        when(namespaceRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listPendingReviews(7L, 0, 20, "user-1", Map.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("namespace.not_found");
    }

    @Test
    void getReviewDetail_taskNotFound_throws() {
        when(reviewTaskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReviewDetail(99L, "user-1", Map.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("review_task.not_found");
    }

    @Test
    void getReviewDetail_namespaceNotFound_throws() {
        ReviewTask task = new ReviewTask(1L, 7L, "submitter-1");
        org.springframework.test.util.ReflectionTestUtils.setField(task, "id", 11L);

        when(reviewTaskRepository.findById(11L)).thenReturn(Optional.of(task));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReviewDetail(11L, "user-1", Map.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("namespace.not_found");
    }

    @Test
    void listReviews_platformScope_canViewReview_namespaceNotFound_throws() {
        ReviewTask task = new ReviewTask(1L, 99L, "submitter-1");
        org.springframework.test.util.ReflectionTestUtils.setField(task, "id", 11L);

        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of("SKILL_ADMIN"));
        when(reviewTaskRepository.findByStatus(eq(ReviewTaskStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(namespaceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listReviews("PENDING", null, 0, 20, "DESC", "user-1", Map.of()))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("namespace.not_found");
    }
}
