package com.iflytek.skillhub.domain.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_request")
public class PasswordResetRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "requested_by_admin", nullable = false)
    private boolean requestedByAdmin = false;

    @Column(name = "requested_by_user_id")
    private String requestedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetRequest() {}

    public PasswordResetRequest(String userId, String email, String tokenHash, Instant expiresAt,
                                boolean requestedByAdmin, String requestedByUserId) {
        this.userId = userId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.requestedByAdmin = requestedByAdmin;
        this.requestedByUserId = requestedByUserId;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void markConsumed() {
        this.consumedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isValid() {
        return !isExpired() && !isConsumed();
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public boolean isRequestedByAdmin() { return requestedByAdmin; }
    public String getRequestedByUserId() { return requestedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
