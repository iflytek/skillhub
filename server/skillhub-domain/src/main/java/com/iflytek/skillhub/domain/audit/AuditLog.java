package com.iflytek.skillhub.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", length = 128)
    private String actorUserId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(String actorUserId,
                    String action,
                    String targetType,
                    Long targetId,
                    String requestId,
                    String clientIp,
                    String userAgent,
                    String detailJson) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.detailJson = detailJson;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getRequestId() { return requestId; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public String getDetailJson() { return detailJson; }
    public Instant getCreatedAt() { return createdAt; }
}
