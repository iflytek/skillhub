package com.iflytek.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "api_token")
public class ApiToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType = "USER";

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_json", nullable = false, columnDefinition = "jsonb")
    private String scopeJson;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApiToken() {}

    public ApiToken(String userId, String name, String tokenPrefix, String tokenHash, String scopeJson) {
        this.subjectType = "USER";
        this.subjectId = userId;
        this.userId = userId;
        this.name = name;
        this.tokenPrefix = tokenPrefix;
        this.tokenHash = tokenHash;
        this.scopeJson = scopeJson;
    }

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(Clock.systemUTC()); }

    public Long getId() { return id; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public String getTokenPrefix() { return tokenPrefix; }
    public String getTokenHash() { return tokenHash; }
    public String getScopeJson() { return scopeJson; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired() { return isExpired(Instant.now(Clock.systemUTC())); }
    public boolean isExpired(Instant referenceTime) { return expiresAt != null && expiresAt.isBefore(referenceTime); }
    public boolean isValid() { return !isRevoked() && !isExpired(); }
    public boolean isValid(Instant referenceTime) { return !isRevoked() && !isExpired(referenceTime); }
}
