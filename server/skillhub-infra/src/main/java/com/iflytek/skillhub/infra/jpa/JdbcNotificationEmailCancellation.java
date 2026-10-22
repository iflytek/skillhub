package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcNotificationEmailCancellation implements NotificationEmailCancellation {
    private final JdbcTemplate jdbc;
    public JdbcNotificationEmailCancellation(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void cancelAll() {
        // Lock batches before item updates, matching inbox/sealing order. SENDING is irreversible.
        jdbc.queryForList("SELECT id FROM notification_email_delivery WHERE status IN ('PENDING','RETRY','CLAIMED') "
                        + "ORDER BY id FOR UPDATE",
                Long.class);
        jdbc.update("""
                UPDATE notification_email_item SET status='SUPPRESSED' WHERE delivery_id IN
                (SELECT id FROM notification_email_delivery WHERE status IN ('PENDING','RETRY','CLAIMED'))
                """);
        jdbc.update("""
                UPDATE notification_email_delivery SET status='SUPPRESSED',result_code='PLATFORM_DISABLED',
                lease_token=NULL,lease_until=NULL WHERE status IN ('PENDING','RETRY','CLAIMED')
                """);
    }

    @Override
    public void cancelCategories(String userId, Set<NotificationCategory> categories) {
        if (categories.isEmpty()) {
            return;
        }
        jdbc.queryForList("SELECT id FROM notification_email_delivery WHERE recipient_id=? AND status IN "
                        + "('PENDING','RETRY','CLAIMED') ORDER BY id FOR UPDATE",
                Long.class, userId);
        for (var category : categories) jdbc.update("""
                UPDATE notification_email_item SET status='SUPPRESSED' WHERE recipient_id=? AND category=? AND delivery_id IN
                (SELECT id FROM notification_email_delivery WHERE status IN ('PENDING','RETRY','CLAIMED'))
                """, userId, category.name());
    }
}
