package com.iflytek.skillhub.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 128)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "body_json", columnDefinition = "TEXT")
    private String bodyJson;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(String recipientId, NotificationCategory category, String eventType,
                        String title, String bodyJson, String entityType, Long entityId,
                        Instant createdAt) {
        this.recipientId = recipientId;
        this.category = category;
        this.eventType = eventType;
        this.title = title;
        this.bodyJson = bodyJson;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdAt = createdAt;
    }

    public void markRead(Instant readAt) {
        this.status = NotificationStatus.READ;
        this.readAt = readAt;
    }

    // Getters
    public Long getId() { return id; }
    public String getRecipientId() { return recipientId; }
    public NotificationCategory getCategory() { return category; }
    public String getEventType() { return eventType; }
    public String getTitle() { return title; }
    public String getBodyJson() { return bodyJson; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public NotificationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
