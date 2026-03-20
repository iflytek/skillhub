package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.report.SkillReportRepository;
import com.iflytek.skillhub.domain.report.SkillReportStatus;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.GovernanceInboxItemResponse;
import com.iflytek.skillhub.dto.GovernanceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class GovernanceWorkbenchAppServiceTest {

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @Mock
    private PromotionRequestRepository promotionRequestRepository;

    @Mock
    private SkillReportRepository skillReportRepository;

    @Mock
    private GovernanceQueryRepository governanceQueryRepository;

    @Mock
    private AdminAuditLogAppService adminAuditLogAppService;

    @Mock
    private GovernanceNotificationService governanceNotificationService;

    private GovernanceWorkbenchAppService service;

    @BeforeEach
    void setUp() {
        service = new GovernanceWorkbenchAppService(
                reviewTaskRepository,
                promotionRequestRepository,
                skillReportRepository,
                governanceQueryRepository,
                adminAuditLogAppService,
                governanceNotificationService
        );
    }

    @Test
    void summary_returnsAllPendingCountsForPlatformGovernor() {
        when(reviewTaskRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(createReviewTask(1L, 11L, 101L, "owner"))));
        when(promotionRequestRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(createPromotionRequest(2L, 101L, 12L, "owner"))));
        when(skillReportRepository.findByStatus(SkillReportStatus.PENDING, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(createReport(3L, 101L, 11L, "reporter"))));
        when(governanceNotificationService.countUnreadNotifications("admin")).thenReturn(4L);

        GovernanceSummaryResponse response = service.getSummary("admin", Map.of(), Set.of("SKILL_ADMIN"));

        assertThat(response.pendingReviews()).isEqualTo(1);
        assertThat(response.pendingPromotions()).isEqualTo(1);
        assertThat(response.pendingReports()).isEqualTo(1);
        assertThat(response.unreadNotifications()).isEqualTo(4);
    }

    @Test
    void summary_limitsReviewsToManagedNamespacesForNamespaceAdmin() {
        when(reviewTaskRepository.findByNamespaceIdAndStatus(11L, ReviewTaskStatus.PENDING, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(createReviewTask(1L, 11L, 101L, "owner"))));
        when(governanceNotificationService.countUnreadNotifications("ns-admin")).thenReturn(2L);

        GovernanceSummaryResponse response = service.getSummary(
                "ns-admin",
                Map.of(11L, NamespaceRole.ADMIN, 12L, NamespaceRole.MEMBER),
                Set.of()
        );

        assertThat(response.pendingReviews()).isEqualTo(1);
        assertThat(response.pendingPromotions()).isZero();
        assertThat(response.pendingReports()).isZero();
        assertThat(response.unreadNotifications()).isEqualTo(2);
    }

    @Test
    void listInbox_combinesReviewPromotionAndReportItems() {
        ReviewTask reviewTask = createReviewTask(1L, 11L, 101L, "owner");
        PromotionRequest promotionRequest = createPromotionRequest(2L, 101L, 12L, "owner");
        SkillReport report = createReport(3L, 101L, 11L, "reporter");

        when(reviewTaskRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(reviewTask)));
        when(promotionRequestRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(promotionRequest)));
        when(skillReportRepository.findByStatus(SkillReportStatus.PENDING, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(governanceQueryRepository.getReviewInboxItems(List.of(reviewTask)))
                .thenReturn(List.of(new GovernanceInboxItemResponse(
                        "REVIEW",
                        1L,
                        "team-a/skill-a@1.0.0",
                        "Pending review",
                        "2026-03-16T02:00:00Z",
                        "team-a",
                        "skill-a"
                )));
        when(governanceQueryRepository.getPromotionInboxItems(List.of(promotionRequest)))
                .thenReturn(List.of(new GovernanceInboxItemResponse(
                        "PROMOTION",
                        2L,
                        "team-a/skill-a@1.0.0",
                        "Promote to @global",
                        "2026-03-16T02:00:00Z",
                        "team-a",
                        "skill-a"
                )));
        when(governanceQueryRepository.getReportInboxItems(List.of(report)))
                .thenReturn(List.of(new GovernanceInboxItemResponse(
                        "REPORT",
                        3L,
                        "team-a/skill-a",
                        "Spam",
                        "2026-03-16T02:00:00Z",
                        "team-a",
                        "skill-a"
                )));

        PageResponse<?> response = service.listInbox("admin", Map.of(), Set.of("SKILL_ADMIN"), null, 0, 20);

        assertThat(response.total()).isEqualTo(3);
        assertThat(response.items()).hasSize(3);
    }

    @Test
    void listActivity_projectsGovernanceAuditEntries() {
        when(adminAuditLogAppService.listAuditLogsByActions(
                eq(0),
                eq(20),
                isNull(),
                eq(Set.of(
                        "REVIEW_SUBMIT",
                        "REVIEW_APPROVE",
                        "REVIEW_REJECT",
                        "REVIEW_WITHDRAW",
                        "PROMOTION_SUBMIT",
                        "PROMOTION_APPROVE",
                        "PROMOTION_REJECT",
                        "REPORT_SKILL",
                        "RESOLVE_SKILL_REPORT",
                        "DISMISS_SKILL_REPORT",
                        "HIDE_SKILL",
                        "ARCHIVE_SKILL",
                        "UNHIDE_SKILL",
                        "UNARCHIVE_SKILL"
                )),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .thenReturn(new PageResponse<>(
                        List.of(
                                new AuditLogItemResponse(
                                        1L,
                                        "REVIEW_APPROVE",
                                        "admin",
                                        "Admin",
                                        "{\"comment\":\"LGTM\"}",
                                        "127.0.0.1",
                                        "req-1",
                                        "REVIEW_TASK",
                                        "99",
                                        Instant.parse("2026-03-16T02:00:00Z")
                                )
                        ),
                        1,
                        0,
                        20
                ));

        PageResponse<?> response = service.listActivity(Set.of("SKILL_ADMIN"), 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void listInbox_reportsStableTotalAcrossPages() {
        ReviewTask reviewTask = createReviewTask(1L, 11L, 101L, "owner");
        ReviewTask laterReviewTask = createReviewTask(2L, 11L, 102L, "owner");
        setField(reviewTask, "submittedAt", Instant.parse("2026-03-16T02:00:00Z"));
        setField(laterReviewTask, "submittedAt", Instant.parse("2026-03-16T03:00:00Z"));

        when(reviewTaskRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(List.of(laterReviewTask, reviewTask), PageRequest.of(0, 2), 2));
        when(governanceQueryRepository.getReviewInboxItems(List.of(laterReviewTask, reviewTask)))
                .thenReturn(List.of(
                        new GovernanceInboxItemResponse(
                                "REVIEW",
                                2L,
                                "team-a/skill-b@1.0.0",
                                "Pending review",
                                "2026-03-16T03:00:00Z",
                                "team-a",
                                "skill-b"
                        ),
                        new GovernanceInboxItemResponse(
                                "REVIEW",
                                1L,
                                "team-a/skill-a@1.0.0",
                                "Pending review",
                                "2026-03-16T02:00:00Z",
                                "team-a",
                                "skill-a"
                        )
                ));

        PageResponse<?> response = service.listInbox("admin", Map.of(), Set.of("SKILL_ADMIN"), "REVIEW", 1, 1);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void listActivity_preservesUnderlyingTotalAcrossPages() {
        when(adminAuditLogAppService.listAuditLogsByActions(
                eq(1),
                eq(20),
                isNull(),
                eq(Set.of(
                        "REVIEW_SUBMIT",
                        "REVIEW_APPROVE",
                        "REVIEW_REJECT",
                        "REVIEW_WITHDRAW",
                        "PROMOTION_SUBMIT",
                        "PROMOTION_APPROVE",
                        "PROMOTION_REJECT",
                        "REPORT_SKILL",
                        "RESOLVE_SKILL_REPORT",
                        "DISMISS_SKILL_REPORT",
                        "HIDE_SKILL",
                        "ARCHIVE_SKILL",
                        "UNHIDE_SKILL",
                        "UNARCHIVE_SKILL"
                )),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .thenReturn(new PageResponse<>(
                        List.of(),
                        42,
                        1,
                        20
                ));

        PageResponse<?> response = service.listActivity(Set.of("SKILL_ADMIN"), 1, 20);

        assertThat(response.total()).isEqualTo(42);
        assertThat(response.page()).isEqualTo(1);
    }

    private ReviewTask createReviewTask(Long id, Long namespaceId, Long skillVersionId, String submittedBy) {
        ReviewTask task = new ReviewTask(skillVersionId, namespaceId, submittedBy);
        setField(task, "id", id);
        return task;
    }

    private PromotionRequest createPromotionRequest(Long id, Long sourceVersionId, Long targetNamespaceId, String submittedBy) {
        PromotionRequest request = new PromotionRequest(sourceVersionId, sourceVersionId, targetNamespaceId, submittedBy);
        setField(request, "id", id);
        setField(request, "sourceSkillId", sourceVersionId);
        return request;
    }

    private SkillReport createReport(Long id, Long skillId, Long namespaceId, String reporterId) {
        SkillReport report = new SkillReport(skillId, namespaceId, reporterId, "Spam", "details");
        setField(report, "id", id);
        return report;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
