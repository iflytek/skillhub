package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
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
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.GovernanceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private SkillRepository skillRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

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
                skillRepository,
                skillVersionRepository,
                namespaceRepository,
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
        stubReviewContext(reviewTask, "team-a", "skill-a");
        stubPromotionContext(promotionRequest, "team-a", "skill-a", "global");
        stubReportContext(report, "team-a", "skill-a");

        when(reviewTaskRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(reviewTask)));
        when(promotionRequestRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(promotionRequest)));
        when(skillReportRepository.findByStatus(SkillReportStatus.PENDING, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(report)));

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
        stubReviewContext(reviewTask, "team-a", "skill-a");
        stubReviewContext(laterReviewTask, "team-a", "skill-b");

        when(reviewTaskRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(List.of(laterReviewTask, reviewTask), PageRequest.of(0, 2), 2));

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

    private void stubReviewContext(ReviewTask task, String namespaceSlug, String skillSlug) {
        SkillVersion version = new SkillVersion(task.getSkillVersionId(), "1.0.0", task.getSubmittedBy());
        setField(version, "id", task.getSkillVersionId());
        setField(version, "skillId", task.getSkillVersionId());
        Skill skill = new Skill(task.getNamespaceId(), skillSlug, task.getSubmittedBy(), SkillVisibility.PUBLIC);
        setField(skill, "id", task.getSkillVersionId());
        Namespace namespace = new Namespace(namespaceSlug, namespaceSlug, task.getSubmittedBy());
        setField(namespace, "id", task.getNamespaceId());
        when(skillVersionRepository.findById(task.getSkillVersionId())).thenReturn(Optional.of(version));
        when(skillRepository.findById(task.getSkillVersionId())).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(task.getNamespaceId())).thenReturn(Optional.of(namespace));
    }

    private void stubPromotionContext(PromotionRequest request, String sourceNamespaceSlug, String skillSlug, String targetNamespaceSlug) {
        Skill skill = new Skill(11L, skillSlug, request.getSubmittedBy(), SkillVisibility.PUBLIC);
        setField(skill, "id", request.getSourceSkillId());
        SkillVersion version = new SkillVersion(request.getSourceSkillId(), "1.0.0", request.getSubmittedBy());
        setField(version, "id", request.getSourceVersionId());
        Namespace sourceNamespace = new Namespace(sourceNamespaceSlug, sourceNamespaceSlug, request.getSubmittedBy());
        setField(sourceNamespace, "id", 11L);
        Namespace targetNamespace = new Namespace(targetNamespaceSlug, targetNamespaceSlug, request.getSubmittedBy());
        setField(targetNamespace, "id", request.getTargetNamespaceId());
        when(skillRepository.findById(request.getSourceSkillId())).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findById(request.getSourceVersionId())).thenReturn(Optional.of(version));
        when(namespaceRepository.findById(11L)).thenReturn(Optional.of(sourceNamespace));
        when(namespaceRepository.findById(request.getTargetNamespaceId())).thenReturn(Optional.of(targetNamespace));
    }

    private void stubReportContext(SkillReport report, String namespaceSlug, String skillSlug) {
        Skill skill = new Skill(report.getNamespaceId(), skillSlug, report.getReporterId(), SkillVisibility.PUBLIC);
        setField(skill, "id", report.getSkillId());
        Namespace namespace = new Namespace(namespaceSlug, namespaceSlug, report.getReporterId());
        setField(namespace, "id", report.getNamespaceId());
        when(skillRepository.findById(report.getSkillId())).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(report.getNamespaceId())).thenReturn(Optional.of(namespace));
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
