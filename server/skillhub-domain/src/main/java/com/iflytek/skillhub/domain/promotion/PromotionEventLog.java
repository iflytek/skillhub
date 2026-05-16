package com.iflytek.skillhub.domain.promotion;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Append-only log row capturing user interactions with a promotion campaign
 * (impressions, clicks, downloads, installs) for effectiveness reporting.
 */
@Entity
@Table(name = "promotion_event_log")
public class PromotionEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private PromotionEventType eventType;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "anonymous_id", length = 128)
    private String anonymousId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PromotionEventLog() {}

    public PromotionEventLog(Long campaignId, PromotionEventType eventType, String userId,
                             String anonymousId, String requestId) {
        this.campaignId = campaignId;
        this.eventType = eventType;
        this.userId = userId;
        this.anonymousId = anonymousId;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public Long getCampaignId() { return campaignId; }
    public PromotionEventType getEventType() { return eventType; }
    public String getUserId() { return userId; }
    public String getAnonymousId() { return anonymousId; }
    public String getRequestId() { return requestId; }
    public Instant getCreatedAt() { return createdAt; }
}
