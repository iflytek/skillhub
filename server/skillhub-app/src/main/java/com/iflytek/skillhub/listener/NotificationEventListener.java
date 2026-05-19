package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.*;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.social.SkillSubscriptionService;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final RecipientResolver recipientResolver;
    private final NotificationDispatcher dispatcher;
    private final SkillSubscriptionService skillSubscriptionService;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(SkillRepository skillRepository,
                                      SkillVersionRepository skillVersionRepository,
                                      NamespaceRepository namespaceRepository,
                                      RecipientResolver recipientResolver,
                                      NotificationDispatcher dispatcher,
                                      SkillSubscriptionService skillSubscriptionService,
                                      ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.recipientResolver = recipientResolver;
        this.dispatcher = dispatcher;
        this.skillSubscriptionService = skillSubscriptionService;
        this.objectMapper = objectMapper;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onSkillPublished(SkillPublishedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            if (!event.publisherId().equals(skill.getCreatedBy())) {
                return;
            }
            String title = "Skill published: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            versionLabel(event.versionId(), body);
            String json = toJson(body);
            dispatcher.dispatch(event.publisherId(), NotificationCategory.PUBLISH,
                    "SKILL_PUBLISHED", title, json, "SKILL", event.skillId());
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onSkillPublishedForSubscribers(SkillPublishedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            List<String> subscribers = skillSubscriptionService.findSubscribersBySkillId(event.skillId());
            if (subscribers.isEmpty()) {
                return;
            }
            String title = "Skill updated: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            versionLabel(event.versionId(), body);
            String json = toJson(body);
            for (String subscriberId : subscribers) {
                if (subscriberId.equals(event.publisherId())) {
                    continue; // skip the publisher
                }
                dispatcher.dispatch(subscriberId, NotificationCategory.PUBLISH,
                        "SUBSCRIPTION_NEW_VERSION", title, json, "SKILL", event.skillId());
            }
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onSkillVersionYankedForSubscribers(SkillVersionYankedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            List<String> subscribers = skillSubscriptionService.findSubscribersBySkillId(event.skillId());
            if (subscribers.isEmpty()) {
                return;
            }
            String title = "Skill version yanked: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            versionLabel(event.versionId(), body);
            String json = toJson(body);
            for (String subscriberId : subscribers) {
                if (subscriberId.equals(event.actorUserId())) {
                    continue; // skip the actor
                }
                dispatcher.dispatch(subscriberId, NotificationCategory.PUBLISH,
                        "SUBSCRIPTION_VERSION_YANKED", title, json, "SKILL", event.skillId());
            }
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "New review submitted for: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("reviewId", event.reviewId());
            body.put("submitterId", event.submitterId());
            versionLabel(event.versionId(), body);
            String json = toJson(body);
            List<String> admins = recipientResolver.resolveNamespaceAdmins(event.namespaceId());
            for (String admin : admins.stream().distinct().toList()) {
                dispatcher.dispatch(admin, NotificationCategory.REVIEW,
                        "REVIEW_SUBMITTED", title, json, "REVIEW", event.reviewId());
            }
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onReviewApproved(ReviewApprovedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "Review approved: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("reviewId", event.reviewId());
            body.put("reviewerId", event.reviewerId());
            versionLabel(event.versionId(), body);
            String json = toJson(body);
            dispatcher.dispatch(event.submitterId(), NotificationCategory.REVIEW,
                    "REVIEW_APPROVED", title, json, "SKILL", event.skillId());
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onReviewRejected(ReviewRejectedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "Review rejected: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("reviewId", event.reviewId());
            body.put("reviewerId", event.reviewerId());
            body.put("reason", event.reason());
            versionLabel(event.versionId(), body);
            String json = toJson(body);
            dispatcher.dispatch(event.submitterId(), NotificationCategory.REVIEW,
                    "REVIEW_REJECTED", title, json, "SKILL", event.skillId());
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onPromotionSubmitted(PromotionSubmittedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "Promotion submitted for: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("promotionId", event.promotionId());
            body.put("submitterId", event.submitterId());
            versionLabel(event.versionId(), body);
            String json = toJson(body);
            List<String> admins = recipientResolver.resolvePlatformSkillAdmins();
            for (String admin : admins.stream().distinct().toList()) {
                dispatcher.dispatch(admin, NotificationCategory.PROMOTION,
                        "PROMOTION_SUBMITTED", title, json, "PROMOTION", event.promotionId());
            }
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onPromotionApproved(PromotionApprovedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "Promotion approved: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("promotionId", event.promotionId());
            body.put("reviewerId", event.reviewerId());
            String json = toJson(body);
            dispatcher.dispatch(event.submitterId(), NotificationCategory.PROMOTION,
                    "PROMOTION_APPROVED", title, json, "SKILL", event.skillId());
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onPromotionRejected(PromotionRejectedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "Promotion rejected: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("promotionId", event.promotionId());
            body.put("reviewerId", event.reviewerId());
            body.put("reason", event.reason());
            String json = toJson(body);
            dispatcher.dispatch(event.submitterId(), NotificationCategory.PROMOTION,
                    "PROMOTION_REJECTED", title, json, "SKILL", event.skillId());
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onReportSubmitted(ReportSubmittedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "Skill reported: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("reportId", event.reportId());
            body.put("reporterId", event.reporterId());
            String json = toJson(body);
            List<String> admins = recipientResolver.resolvePlatformSkillAdmins();
            for (String admin : admins.stream().distinct().toList()) {
                dispatcher.dispatch(admin, NotificationCategory.REPORT,
                        "REPORT_SUBMITTED", title, json, "REPORT", event.reportId());
            }
        });
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener
    public void onReportResolved(ReportResolvedEvent event) {
        skillRepository.findById(event.skillId()).ifPresent(skill -> {
            String title = "Report resolved: " + skillDisplayName(skill);
            Map<String, Object> body = bodyWithSkill(skill);
            body.put("reportId", event.reportId());
            body.put("handlerId", event.handlerId());
            body.put("action", event.action());
            String json = toJson(body);
            dispatcher.dispatch(event.reporterId(), NotificationCategory.REPORT,
                    "REPORT_RESOLVED", title, json, "SKILL", event.skillId());
        });
    }

    // --- helpers ---

    private String skillDisplayName(Skill skill) {
        String name = skill.getDisplayName();
        return (name != null && !name.isBlank()) ? name : skill.getSlug();
    }

    private Map<String, Object> bodyWithSkill(Skill skill) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("skillId", skill.getId());
        map.put("skillName", skillDisplayName(skill));
        map.put("slug", skill.getSlug());
        namespaceRepository.findById(skill.getNamespaceId())
                .ifPresent(namespace -> map.put("namespace", namespace.getSlug()));
        return map;
    }

    private void versionLabel(Long versionId, Map<String, Object> body) {
        if (versionId != null) {
            skillVersionRepository.findById(versionId).ifPresent(v ->
                    body.put("version", v.getVersion()));
        }
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize notification body", e);
            return "{}";
        }
    }
}
