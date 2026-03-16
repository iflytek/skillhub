package com.iflytek.skillhub.domain.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_notification")
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "body_json", columnDefinition = "TEXT")
    private String bodyJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserNotificationStatus status = UserNotificationStatus.UNREAD;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected UserNotification() {
    }

    public UserNotification(String userId,
                            String category,
                            String entityType,
                            Long entityId,
                            String title,
                            String bodyJson) {
        this.userId = userId;
        this.category = category;
        this.entityType = entityType;
        this.entityId = entityId;
        this.title = title;
        this.bodyJson = bodyJson;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markRead() {
        this.status = UserNotificationStatus.READ;
        this.readAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCategory() {
        return category;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getTitle() {
        return title;
    }

    public String getBodyJson() {
        return bodyJson;
    }

    public UserNotificationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
