package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Closing a switch records a constant-size barrier; background work suppresses old items in bounded pages. */
@Repository
public class JdbcNotificationEmailCancellation implements NotificationEmailCancellation {
    private final JdbcTemplate jdbc;

    public JdbcNotificationEmailCancellation(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void cancelAll() {
        barrier("GLOBAL", null, null);
    }

    @Override
    public void cancelCategories(String userId, Set<NotificationCategory> categories) {
        for (var category : categories.stream().sorted().toList()) {
            barrier("USER:" + userId + ":" + category.name(), userId, category.name());
        }
    }

    private void barrier(String key, String user, String category) {
        // The caller holds policy/account/contact/preferences guards. Inbox allocates item IDs only after
        // those guards, so a concurrent same-scope admission either commits before this cutoff or after it.
        Long cutoff = jdbc.queryForObject("SELECT coalesce(max(id),0) FROM notification_email_item", Long.class);
        jdbc.update("""
                INSERT INTO notification_email_cancel_barrier(scope_key,recipient_id,category,through_item_id,updated_at)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(scope_key) DO UPDATE
                SET through_item_id=GREATEST(notification_email_cancel_barrier.through_item_id,EXCLUDED.through_item_id),
                updated_at=EXCLUDED.updated_at
                """, key, user, category, cutoff);
    }
}
