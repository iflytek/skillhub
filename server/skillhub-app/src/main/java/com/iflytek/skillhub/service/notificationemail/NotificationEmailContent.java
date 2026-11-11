package com.iflytek.skillhub.service.notificationemail;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailItem;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Safe event descriptions only: private resource names and review comments stay behind login. */
final class NotificationEmailContent {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    record Content(String subject, String text) {}

    static Content render(String locale, String kind, long count, List<NotificationEmailItem> items, String link) {
        boolean zh = "zh".equals(locale);
        boolean immediate = "IMMEDIATE".equals(kind);
        String subject = immediate && items.size() == 1
                ? "SkillHub · " + eventLabel(items.getFirst(), zh)
                : zh ? "SkillHub 通知摘要" : "SkillHub notification summary";
        StringBuilder text = new StringBuilder(immediate
                ? zh ? "收到一条新的 SkillHub 通知。\n" : "You have a new SkillHub notification.\n"
                : zh ? "以下是本次汇总的 SkillHub 通知。\n" : "Here is your SkillHub notification summary.\n");
        text.append(zh ? "本批通知：" : "Notifications in this batch: ").append(count)
                .append(zh ? "条。\n" : ".\n");
        if (count > items.size()) {
            text.append(zh ? "邮件仅展示部分通知预览；完整可访问内容请登录查看。\n"
                           : "This email shows a limited preview. Sign in to see all accessible details.\n");
        }
        for (var category : NotificationCategory.values()) {
            var section = items.stream().filter(item -> item.category() == category).toList();
            if (section.isEmpty()) continue;
            text.append('\n').append(categoryLabel(category, zh)).append(zh ? "（预览）\n" : " (preview)\n");
            for (var item : section) {
                text.append(eventLabel(item, zh)).append(" · ").append(TIME.format(item.occurredAt())).append('\n');
            }
        }
        text.append(zh ? "\n查看通知详情：\n" : "\nView notification details:\n").append(link).append('\n')
                .append(zh ? "\n请使用收到本邮件的 SkillHub 账号登录，勿使用其他账号。\n详情会重新核对当前权限；已失去访问权限的内容不会展示。\n"
                           : "\nSign in with the SkillHub account that received this email, not another account.\nDetails are checked against your current access; unavailable content is not shown.\n");
        return new Content(subject, text.toString());
    }

    private static String categoryLabel(NotificationCategory category, boolean zh) {
        return switch (category) {
            case PUBLISH -> zh ? "发布动态" : "Publishing";
            case REVIEW -> zh ? "审核进度" : "Reviews";
            case PROMOTION -> zh ? "全局提升" : "Promotions";
            case REPORT -> zh ? "举报处理" : "Reports";
        };
    }

    private static String eventLabel(NotificationEmailItem item, boolean zh) {
        return switch (item.eventType()) {
            case "SUITE_REVIEW_SUBMITTED" -> zh ? "新的套件审核申请待处理" : "A new suite review needs your attention";
            case "SUITE_REVIEW_APPROVED" -> zh ? "套件版本审核已通过" : "Your suite version review was approved";
            case "SUITE_REVIEW_REJECTED" -> zh ? "套件版本审核未通过" : "Your suite version review was rejected";
            case "REVIEW_SUBMITTED" -> zh ? "新的技能审核申请待处理" : "A new skill review needs your attention";
            case "REVIEW_APPROVED" -> zh ? "技能版本审核已通过" : "Your skill version review was approved";
            case "REVIEW_REJECTED" -> zh ? "技能版本审核未通过" : "Your skill version review was rejected";
            case "PROFILE_REVIEW_SUBMITTED" -> zh ? "新的个人资料审核申请待处理" : "A new profile review needs your attention";
            case "SKILL_PUBLISHED" -> zh ? "技能版本已发布" : "Your skill version was published";
            case "SUBSCRIPTION_NEW_VERSION" -> zh ? "订阅的技能有新版本" : "A subscribed skill has a new version";
            case "SUBSCRIPTION_VERSION_YANKED" -> zh ? "订阅的技能版本已撤下" : "A subscribed skill version was withdrawn";
            case "PROMOTION_SUBMITTED" -> zh ? "新的全局提升申请待处理" : "A new promotion request needs your attention";
            case "PROMOTION_APPROVED" -> zh ? "全局提升申请已通过" : "Your promotion request was approved";
            case "PROMOTION_REJECTED" -> zh ? "全局提升申请未通过" : "Your promotion request was rejected";
            case "REPORT_SUBMITTED" -> zh ? "新的举报待处理" : "A new report needs your attention";
            case "REPORT_RESOLVED" -> zh ? "举报处理结果已更新" : "A report resolution is available";
            default -> zh ? categoryLabel(item.category(), true) + "有新通知"
                          : "A new notification about " + categoryLabel(item.category(), false).toLowerCase(java.util.Locale.ROOT);
        };
    }
}
