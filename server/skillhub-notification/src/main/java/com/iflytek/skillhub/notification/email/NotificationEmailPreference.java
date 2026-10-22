package com.iflytek.skillhub.notification.email;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_email_preference",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category"}))
public class NotificationEmailPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, length = 128) private String userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private NotificationCategory category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationEmailMode mode = NotificationEmailMode.OFF;
    @Column(name = "enabled_since") private java.time.Instant enabledSince;
    @Version private long version;
    protected NotificationEmailPreference() {}
    public NotificationEmailPreference(String userId, NotificationCategory category) {
        this.userId = userId;
        this.category = category;
    }
    public NotificationCategory getCategory() {
        return category;
    }
    public NotificationEmailMode getMode() {
        return mode;
    }
    public java.time.Instant getEnabledSince() {
        return enabledSince;
    }
    public void setMode(NotificationEmailMode mode, java.time.Instant now) {
        java.util.Objects.requireNonNull(mode);
        if (this.mode == NotificationEmailMode.OFF && mode != NotificationEmailMode.OFF)
            enabledSince = java.util.Objects.requireNonNull(now);
        this.mode = mode;
    }
}
