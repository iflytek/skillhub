package com.iflytek.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
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
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
    void prePersist() { this.createdAt = LocalDateTime.now(); }

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
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired() { return expiresAt != null && expiresAt.isBefore(LocalDateTime.now()); }
    public boolean isValid() { return !isRevoked() && !isExpired(); }
}
