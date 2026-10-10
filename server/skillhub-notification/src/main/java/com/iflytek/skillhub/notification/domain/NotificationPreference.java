package com.iflytek.skillhub.notification.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_preference",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category", "channel"}))
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(nullable = false)
    private boolean enabled = true;

    protected NotificationPreference() {}

    public NotificationPreference(String userId, NotificationCategory category,
                                   NotificationChannel channel, boolean enabled) {
        this.userId = userId;
        this.category = category;
        this.channel = channel;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public NotificationCategory getCategory() { return category; }
    public NotificationChannel getChannel() { return channel; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
