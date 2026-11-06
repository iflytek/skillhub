package com.iflytek.skillhub.service.notificationemail;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailItem;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationEmailContentTest {
    private NotificationEmailItem item(String type) {
        return new NotificationEmailItem(1, 1, "user", "event", NotificationCategory.REVIEW, type,
                null, 12L, Instant.parse("2026-11-06T02:29:39Z"), Instant.EPOCH, "ACTIVE");
    }

    @Test
    void immediateSuiteReviewExplainsActionAccountAndReadableTime() {
        var mail = NotificationEmailContent.render("zh", "IMMEDIATE", 1,
                List.of(item("SUITE_REVIEW_SUBMITTED")), "https://example.test/details/1");
        assertThat(mail.subject()).contains("新的套件审核申请待处理").doesNotContain("摘要");
        assertThat(mail.text()).contains("本批通知：1条", "2026-11-06 02:29:39 UTC", "收到本邮件的 SkillHub 账号",
                "查看通知详情：\nhttps://example.test/details/1").doesNotContain("2026-11-06T", "原始通知记录数");
    }

    @Test
    void digestPreservesOriginalCountAndExplainsLimitedPreview() {
        var mail = NotificationEmailContent.render("en", "DIGEST", 55,
                List.of(item("SUITE_REVIEW_APPROVED"), item("SUITE_REVIEW_REJECTED")), "https://example.test/details/2");
        assertThat(mail.subject()).contains("summary");
        assertThat(mail.text()).contains("Notifications in this batch: 55", "limited preview", "was approved",
                "was rejected", "current access", "SkillHub account that received this email");
    }

    @Test
    void unrecognizedEventDoesNotExposeArbitraryEventText() {
        var mail = NotificationEmailContent.render("zh", "IMMEDIATE", 1,
                List.of(item("PRIVATE_SECRET\r\n<script>")), "https://example.test/details/3");
        assertThat(mail.subject()).isEqualTo("SkillHub · 审核进度有新通知");
        assertThat(mail.text()).doesNotContain("PRIVATE_SECRET", "script");
    }
}
