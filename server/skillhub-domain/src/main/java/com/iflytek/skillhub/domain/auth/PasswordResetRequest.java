package com.iflytek.skillhub.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "password_reset_request")
public class PasswordResetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "requested_by_admin", nullable = false)
    private boolean requestedByAdmin;

    @Column(name = "requested_by_user_id", length = 128)
    private String requestedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetRequest() {
    }

    public PasswordResetRequest(String userId,
                                String email,
                                String codeHash,
                                Instant expiresAt,
                                boolean requestedByAdmin,
                                String requestedByUserId) {
        this.userId = userId;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.requestedByAdmin = requestedByAdmin;
        this.requestedByUserId = requestedByUserId;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
    }

    public void markConsumed(Instant timestamp) {
        this.consumedAt = timestamp;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public boolean isRequestedByAdmin() {
        return requestedByAdmin;
    }

    public String getRequestedByUserId() {
        return requestedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
