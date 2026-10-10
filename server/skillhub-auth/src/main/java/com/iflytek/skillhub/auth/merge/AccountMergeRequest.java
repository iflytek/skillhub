package com.iflytek.skillhub.auth.merge;

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
@Table(name = "account_merge_request")
public class AccountMergeRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "primary_user_id", nullable = false, length = 128)
    private String primaryUserId;

    @Column(name = "secondary_user_id", nullable = false, length = 128)
    private String secondaryUserId;

    @Column(nullable = false, length = 32)
    private String status = STATUS_PENDING;

    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountMergeRequest() {}

    public AccountMergeRequest(String primaryUserId,
                               String secondaryUserId,
                               String verificationToken,
                               Instant tokenExpiresAt) {
        this.primaryUserId = primaryUserId;
        this.secondaryUserId = secondaryUserId;
        this.verificationToken = verificationToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.status = STATUS_PENDING;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public String getPrimaryUserId() { return primaryUserId; }
    public String getSecondaryUserId() { return secondaryUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
