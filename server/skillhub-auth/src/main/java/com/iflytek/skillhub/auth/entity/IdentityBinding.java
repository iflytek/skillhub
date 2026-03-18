package com.iflytek.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "identity_binding",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider_code", "subject"}))
public class IdentityBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(nullable = false, length = 256)
    private String subject;

    @Column(name = "login_name", length = 128)
    private String loginName;

    @Column(name = "extra_json", columnDefinition = "jsonb")
    private String extraJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdentityBinding() {}

    public IdentityBinding(String userId, String providerCode, String subject, String loginName) {
        this.userId = userId;
        this.providerCode = providerCode;
        this.subject = subject;
        this.loginName = loginName;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
