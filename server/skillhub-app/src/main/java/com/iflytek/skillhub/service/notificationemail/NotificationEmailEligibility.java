package com.iflytek.skillhub.service.notificationemail;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.social.*;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.listener.RecipientResolver;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailItem;
import java.util.*;
import org.springframework.stereotype.Component;

/** Reuses the existing recipient and metadata access policies, without retaining private notification bodies. */
@Component
public class NotificationEmailEligibility {
    private final SkillRepository skills;
    private final NamespaceRepository namespaces;
    private final UserAccountRepository accounts;
    private final NamespaceMemberRepository members;
    private final SkillSubscriptionRepository subscriptions;
    private final SubscriptionMetadataAccessPolicy access;
    private final RecipientResolver recipients;
    public NotificationEmailEligibility(SkillRepository skills, NamespaceRepository namespaces,
            UserAccountRepository accounts, NamespaceMemberRepository members,
            SkillSubscriptionRepository subscriptions, SubscriptionMetadataAccessPolicy access,
            RecipientResolver recipients) {
        this.skills = skills;
        this.namespaces = namespaces;
        this.accounts = accounts;
        this.members = members;
        this.subscriptions = subscriptions;
        this.access = access;
        this.recipients = recipients;
    }

    public boolean eligible(NotificationEmailItem item) {
        String user = item.recipientId(), type = item.eventType();
        if (accounts.findById(user).filter(account -> account.isActive()).isEmpty()) {
            return false;
        }
        if ("PROFILE_REVIEW_SUBMITTED".equals(type)) {
            return recipients.resolvePlatformUserAdmins().contains(user);
        }
        Skill skill = item.skillId() == null ? null : skills.findById(item.skillId()).orElse(null);
        if (skill == null) {
            return false;
        }
        if ("PROMOTION_SUBMITTED".equals(type) || "REPORT_SUBMITTED".equals(type)) {
            return recipients.resolvePlatformSkillAdmins().contains(user);
        }
        if ("REVIEW_SUBMITTED".equals(type)) {
            return recipients.resolveNamespaceAdmins(skill.getNamespaceId()).contains(user);
        }
        var account = accounts.findById(user).orElse(null);
        var namespace = namespaces.findById(skill.getNamespaceId()).orElse(null);
        Map<Long, NamespaceRole> roles = members.findByNamespaceIdAndUserId(skill.getNamespaceId(), user)
                                                 .map(member -> Map.of(skill.getNamespaceId(), member.getRole()))
                                                 .orElse(Map.of());
        if (type.startsWith("SUBSCRIPTION_")) {
            if (subscriptions.findBySkillIdAndUserId(skill.getId(), user).isEmpty()) {
                return false;
            }
            return "SUBSCRIPTION_VERSION_YANKED".equals(type)
                    ? access.canAccessYankedPublication(skill, namespace, account, roles, true)
                    : access.canAccessCurrent(skill, namespace, account, roles);
        }
        return access.canAccessCurrent(skill, namespace, account, roles);
    }

    public String targetPath(NotificationEmailItem item) {
        return switch (item.eventType()) {
            case "PROFILE_REVIEW_SUBMITTED" -> "/dashboard/reviews?type=profile";
            case "REVIEW_SUBMITTED" -> "/dashboard/reviews/" + item.entityId();
            case "PROMOTION_SUBMITTED" -> "/dashboard/promotions";
            case "REPORT_SUBMITTED" -> "/dashboard/reports";
            default -> skillPath(item.skillId());
        };
    }

    private String skillPath(Long id) {
        return skills.findById(id)
                .flatMap(skill
                        -> namespaces.findById(skill.getNamespaceId())
                                .map(namespace -> "/space/" + namespace.getSlug() + "/" + skill.getSlug()))
                .orElse("/dashboard/notifications");
    }
}
