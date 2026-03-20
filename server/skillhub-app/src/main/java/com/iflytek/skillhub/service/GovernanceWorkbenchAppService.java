package com.iflytek.skillhub.service;

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
import com.iflytek.skillhub.dto.GovernanceActivityItemResponse;
import com.iflytek.skillhub.dto.GovernanceInboxItemResponse;
import com.iflytek.skillhub.dto.GovernanceSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Application-facing aggregation service for the governance workbench.
 *
 * <p>It joins review, promotion, report, namespace, and audit sources into the
 * composite read models consumed by governance screens.
 */
@Service
public class GovernanceWorkbenchAppService {

    private static final int SUMMARY_PAGE_SIZE = 100;
    private static final Set<String> ACTIVITY_ACTIONS = Set.of(
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
    );

    private final ReviewTaskRepository reviewTaskRepository;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillReportRepository skillReportRepository;
    private final GovernanceQueryRepository governanceQueryRepository;
    private final AdminAuditLogAppService adminAuditLogAppService;
    private final GovernanceNotificationService governanceNotificationService;

    public GovernanceWorkbenchAppService(ReviewTaskRepository reviewTaskRepository,
                                         PromotionRequestRepository promotionRequestRepository,
                                         SkillReportRepository skillReportRepository,
                                         GovernanceQueryRepository governanceQueryRepository,
                                         AdminAuditLogAppService adminAuditLogAppService,
                                         GovernanceNotificationService governanceNotificationService) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillReportRepository = skillReportRepository;
        this.governanceQueryRepository = governanceQueryRepository;
        this.adminAuditLogAppService = adminAuditLogAppService;
        this.governanceNotificationService = governanceNotificationService;
    }

    /**
     * Returns top-level counts for the governance dashboard, scoped by the
     * caller's namespace and platform roles.
     */
    public GovernanceSummaryResponse getSummary(String userId,
                                                Map<Long, NamespaceRole> namespaceRoles,
                                                Set<String> platformRoles) {
        return new GovernanceSummaryResponse(
                visiblePendingReviews(namespaceRoles, platformRoles, SUMMARY_PAGE_SIZE).getTotalElements(),
                hasPlatformGovernanceRole(platformRoles)
                        ? promotionRequestRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, SUMMARY_PAGE_SIZE)).getTotalElements()
                        : 0,
                hasPlatformGovernanceRole(platformRoles)
                        ? skillReportRepository.findByStatus(SkillReportStatus.PENDING, PageRequest.of(0, SUMMARY_PAGE_SIZE)).getTotalElements()
                        : 0,
                governanceNotificationService.countUnreadNotifications(userId)
        );
    }

    /**
     * Builds the governance inbox by combining pending reviews, promotions, and
     * reports that the caller is allowed to see.
     */
    public PageResponse<GovernanceInboxItemResponse> listInbox(String userId,
                                                               Map<Long, NamespaceRole> namespaceRoles,
                                                               Set<String> platformRoles,
                                                               String type,
                                                               int page,
                                                               int size) {
        int fetchSize = Math.max((page + 1) * size, size);
        List<GovernanceInboxItemResponse> items = new ArrayList<>();
        long total = 0;
        boolean includeAll = type == null || type.isBlank();
        if (includeAll || "REVIEW".equalsIgnoreCase(type)) {
            Page<ReviewTask> reviews = visiblePendingReviews(namespaceRoles, platformRoles, fetchSize);
            total += reviews.getTotalElements();
            items.addAll(governanceQueryRepository.getReviewInboxItems(reviews.getContent()));
        }
        if (hasPlatformGovernanceRole(platformRoles) && (includeAll || "PROMOTION".equalsIgnoreCase(type))) {
            Page<PromotionRequest> promotions = promotionRequestRepository.findByStatus(
                    ReviewTaskStatus.PENDING,
                    PageRequest.of(0, fetchSize)
            );
            total += promotions.getTotalElements();
            items.addAll(governanceQueryRepository.getPromotionInboxItems(promotions.getContent()));
        }
        if (hasPlatformGovernanceRole(platformRoles) && (includeAll || "REPORT".equalsIgnoreCase(type))) {
            Page<SkillReport> reports = skillReportRepository.findByStatus(
                    SkillReportStatus.PENDING,
                    PageRequest.of(0, fetchSize)
            );
            total += reports.getTotalElements();
            items.addAll(governanceQueryRepository.getReportInboxItems(reports.getContent()));
        }
        items.sort(Comparator.comparing(
                GovernanceInboxItemResponse::timestamp,
                Comparator.nullsLast(String::compareTo)
        ).reversed());
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return new PageResponse<>(items.subList(fromIndex, toIndex), total, page, size);
    }

    /**
     * Returns audit-derived governance activity entries for callers with
     * platform-wide visibility.
     */
    public PageResponse<GovernanceActivityItemResponse> listActivity(Set<String> platformRoles, int page, int size) {
        if (!canReadActivity(platformRoles)) {
            return new PageResponse<>(List.of(), 0, page, size);
        }
        PageResponse<AuditLogItemResponse> raw = adminAuditLogAppService.listAuditLogsByActions(
                page,
                size,
                null,
                ACTIVITY_ACTIONS,
                null,
                null,
                null,
                null,
                null,
                null
        );
        List<GovernanceActivityItemResponse> items = raw.items().stream()
                .map(item -> new GovernanceActivityItemResponse(
                        item.id(),
                        item.action(),
                        item.userId(),
                        item.username(),
                        item.resourceType(),
                        item.resourceId(),
                        item.details(),
                        item.timestamp() != null ? item.timestamp().toString() : null
                ))
                .toList();
        return new PageResponse<>(items, raw.total(), raw.page(), raw.size());
    }

    private Page<ReviewTask> visiblePendingReviews(Map<Long, NamespaceRole> namespaceRoles,
                                                   Set<String> platformRoles,
                                                   int size) {
        if (hasPlatformGovernanceRole(platformRoles)) {
            return reviewTaskRepository.findByStatus(ReviewTaskStatus.PENDING, PageRequest.of(0, size));
        }
        List<Page<ReviewTask>> pages = namespaceRoles.entrySet().stream()
                .filter(entry -> entry.getValue() == NamespaceRole.OWNER || entry.getValue() == NamespaceRole.ADMIN)
                .map(entry -> reviewTaskRepository.findByNamespaceIdAndStatus(entry.getKey(), ReviewTaskStatus.PENDING, PageRequest.of(0, size)))
                .toList();
        List<ReviewTask> tasks = pages.stream()
                .flatMap(pageResult -> pageResult.getContent().stream())
                .toList();
        long total = pages.stream().mapToLong(Page::getTotalElements).sum();
        return new org.springframework.data.domain.PageImpl<>(tasks, PageRequest.of(0, size), total);
    }

    private boolean hasPlatformGovernanceRole(Set<String> platformRoles) {
        return platformRoles.contains("SKILL_ADMIN") || platformRoles.contains("SUPER_ADMIN");
    }

    private boolean canReadActivity(Set<String> platformRoles) {
        return hasPlatformGovernanceRole(platformRoles)
                || platformRoles.contains("AUDITOR");
    }
}
