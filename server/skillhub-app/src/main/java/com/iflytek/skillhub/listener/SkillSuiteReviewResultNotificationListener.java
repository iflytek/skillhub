package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillSuiteReviewCompletedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Review outcomes belong to the submitter, independently of reviewer notifications. */
@Component
public class SkillSuiteReviewResultNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(SkillSuiteReviewResultNotificationListener.class);
    private final ReviewTaskRepository reviews;
    private final SkillSuiteRepository suites;
    private final SkillSuiteVersionRepository versions;
    private final NamespaceRepository namespaces;
    private final NotificationService notifications;
    private final ObjectMapper mapper;

    public SkillSuiteReviewResultNotificationListener(ReviewTaskRepository reviews, SkillSuiteRepository suites,
            SkillSuiteVersionRepository versions, NamespaceRepository namespaces,
            NotificationService notifications, ObjectMapper mapper) {
        this.reviews = reviews;
        this.suites = suites;
        this.versions = versions;
        this.namespaces = namespaces;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onReviewCompleted(SkillSuiteReviewCompletedEvent event) {
        var task = reviews.findById(event.reviewId()).orElse(null);
        if (task == null || task.getSubjectType() != ReviewSubjectType.SUITE_VERSION
                || task.getStatus() != event.status()
                || !Objects.equals(task.getSubjectId(), event.suiteId())
                || !Objects.equals(task.getSubjectVersionId(), event.versionId())
                || !Objects.equals(task.getNamespaceId(), event.namespaceId())
                || !Objects.equals(task.getSubmittedBy(), event.submitterId())) {
            return;
        }
        var suite = suites.findById(event.suiteId()).orElse(null);
        if (suite == null || !Objects.equals(suite.getNamespaceId(), event.namespaceId())) {
            return;
        }
        var version = versions.findById(event.versionId()).orElse(null);
        if (version == null || !Objects.equals(version.getSuiteId(), event.suiteId())) {
            return;
        }
        var namespace = namespaces.findById(event.namespaceId()).orElse(null);
        if (namespace == null) {
            return;
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("suiteId", suite.getId());
        body.put("suiteName", version.getDisplayName() == null || version.getDisplayName().isBlank()
                ? suite.getSlug() : version.getDisplayName());
        body.put("namespace", namespace.getSlug());
        body.put("slug", suite.getSlug());
        body.put("version", version.getVersion());
        body.put("reviewId", event.reviewId());
        body.put("submitterId", event.submitterId());
        body.put("eventId", event.eventId());
        body.put("occurredAt", event.occurredAt().toString());
        boolean approved = event.status() == ReviewTaskStatus.APPROVED;
        try {
            notifications.create(event.submitterId(), NotificationCategory.REVIEW,
                    approved ? "SUITE_REVIEW_APPROVED" : "SUITE_REVIEW_REJECTED",
                    approved ? "Suite review approved" : "Suite review rejected",
                    mapper.writeValueAsString(body), "REVIEW", event.reviewId(), event.eventId(), event.occurredAt());
        } catch (JsonProcessingException | RuntimeException failure) {
            log.warn("Failed to create Suite review result notification [reviewId={}, recipientId={}]",
                    event.reviewId(), event.submitterId());
        }
    }
}
