package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillSuiteReviewSubmittedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
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
import java.util.Map;
import java.util.Objects;

/** Keeps Suite review notifications separate from Skill-specific event payloads. */
@Component
public class SkillSuiteReviewNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SkillSuiteReviewNotificationListener.class);

    private final SkillSuiteRepository suiteRepository;
    private final SkillSuiteVersionRepository versionRepository;
    private final NamespaceRepository namespaceRepository;
    private final RecipientResolver recipientResolver;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public SkillSuiteReviewNotificationListener(
            SkillSuiteRepository suiteRepository,
            SkillSuiteVersionRepository versionRepository,
            NamespaceRepository namespaceRepository,
            RecipientResolver recipientResolver,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.suiteRepository = suiteRepository;
        this.versionRepository = versionRepository;
        this.namespaceRepository = namespaceRepository;
        this.recipientResolver = recipientResolver;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onReviewSubmitted(SkillSuiteReviewSubmittedEvent event) {
        var suite = suiteRepository.findById(event.suiteId()).orElse(null);
        if (suite == null || !Objects.equals(suite.getNamespaceId(), event.namespaceId())) {
            return;
        }
        var version = versionRepository.findById(event.versionId()).orElse(null);
        if (version == null || !Objects.equals(version.getSuiteId(), suite.getId())) {
            return;
        }
        var namespace = namespaceRepository.findById(event.namespaceId()).orElse(null);
        if (namespace == null) {
            return;
        }

        String displayName = version.getDisplayName();
        String name = displayName != null && !displayName.isBlank() ? displayName : suite.getSlug();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("suiteId", suite.getId());
        body.put("suiteName", name);
        body.put("namespace", namespace.getSlug());
        body.put("slug", suite.getSlug());
        body.put("version", version.getVersion());
        body.put("reviewId", event.reviewId());
        body.put("submitterId", event.submitterId());
        String json = toJson(body, event.reviewId());
        var recipients = namespace.getType() == NamespaceType.GLOBAL
                ? recipientResolver.resolvePlatformSkillAdmins()
                : recipientResolver.resolveNamespaceAdmins(event.namespaceId());
        for (String recipient : recipients.stream().distinct().toList()) {
            try {
                notificationService.create(recipient, NotificationCategory.REVIEW,
                        "SUITE_REVIEW_SUBMITTED", "Suite review submitted",
                        json, "REVIEW", event.reviewId());
            } catch (RuntimeException exception) {
                log.warn("Failed to create Suite review notification [reviewId={}, recipientId={}]",
                        event.reviewId(), recipient);
            }
        }
    }

    private String toJson(Map<String, Object> body, Long reviewId) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize Suite review notification [reviewId={}]", reviewId);
            return "{}";
        }
    }
}
