package com.iflytek.skillhub.auth.connection.secret;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Encrypted-at-rest Secret version whose raw envelope is restricted to this package. */
@Entity
@Table(name = "login_connection_secret_version")
public class LoginConnectionSecretVersion {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", length = 64, updatable = false)
    private String organizationId;

    @Column(name = "scope_key", length = 64, insertable = false, updatable = false)
    private String scopeKey;

    @Column(name = "connection_id", nullable = false, length = 64, updatable = false)
    private String connectionId;

    @Column(nullable = false, length = 128, updatable = false)
    private String purpose;

    @Column(name = "binding_version", nullable = false, updatable = false)
    private long bindingVersion;

    @Column(nullable = false, length = 32, updatable = false)
    private String algorithm;

    @Column(name = "key_id", nullable = false, length = 128, updatable = false)
    private String keyId;

    @Column(nullable = false, updatable = false)
    private byte[] nonce;

    @Column(nullable = false, updatable = false)
    private byte[] ciphertext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LoginConnectionSecretStatus status;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_by", nullable = false, length = 128, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_by", length = 128)
    private String revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected LoginConnectionSecretVersion() {
    }

    private LoginConnectionSecretVersion(
            String organizationId,
            String connectionId,
            SecretPurpose purpose,
            long bindingVersion,
            EncryptedSecretEnvelope envelope,
            String createdBy,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = organizationId == null
                ? null
                : requireText(organizationId, "organizationId");
        this.connectionId = requireText(connectionId, "connectionId");
        this.purpose = Objects.requireNonNull(purpose, "purpose").value();
        if (bindingVersion < 1) {
            throw new IllegalArgumentException("bindingVersion must be positive");
        }
        this.bindingVersion = bindingVersion;
        EncryptedSecretEnvelope requiredEnvelope = Objects.requireNonNull(envelope, "envelope");
        this.algorithm = requiredEnvelope.algorithm();
        this.keyId = requiredEnvelope.keyId();
        this.nonce = requiredEnvelope.nonce();
        this.ciphertext = requiredEnvelope.ciphertext();
        this.status = LoginConnectionSecretStatus.CURRENT;
        this.createdBy = requireText(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    static LoginConnectionSecretVersion current(
            String organizationId,
            String connectionId,
            SecretPurpose purpose,
            long bindingVersion,
            EncryptedSecretEnvelope envelope,
            String createdBy,
            Instant createdAt
    ) {
        return new LoginConnectionSecretVersion(
                organizationId,
                connectionId,
                purpose,
                bindingVersion,
                envelope,
                createdBy,
                createdAt
        );
    }

    void retireUntil(Instant retirementDeadline, Instant occurredAt) {
        if (status != LoginConnectionSecretStatus.CURRENT) {
            throw new IllegalStateException("Only the current Secret can enter retirement");
        }
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        Instant deadline = Objects.requireNonNull(retirementDeadline, "retirementDeadline");
        if (!deadline.isAfter(transitionTime)) {
            throw new IllegalArgumentException("Retirement deadline must be in the future");
        }
        status = LoginConnectionSecretStatus.RETIRING;
        validUntil = deadline;
        updatedAt = transitionTime;
    }

    void revoke(String actorId, Instant occurredAt) {
        if (status == LoginConnectionSecretStatus.REVOKED) {
            return;
        }
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        status = LoginConnectionSecretStatus.REVOKED;
        revokedBy = requireText(actorId, "actorId");
        revokedAt = transitionTime;
        updatedAt = transitionTime;
    }

    boolean isResolvableAt(Instant instant) {
        Instant now = Objects.requireNonNull(instant, "instant");
        return status == LoginConnectionSecretStatus.CURRENT
                || (status == LoginConnectionSecretStatus.RETIRING
                && validUntil != null
                && now.isBefore(validUntil));
    }

    EncryptedSecretEnvelope envelope() {
        return new EncryptedSecretEnvelope(algorithm, keyId, nonce, ciphertext);
    }

    SecretReference reference() {
        return new SecretReference(
                organizationId == null ? SecretReference.PLATFORM_SCOPE_KEY : organizationId,
                connectionId,
                new SecretPurpose(purpose),
                bindingVersion
        );
    }

    private Instant requireCurrentOrLater(Instant occurredAt) {
        Instant transitionTime = Objects.requireNonNull(occurredAt, "occurredAt");
        if (transitionTime.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Secret transition timestamp is stale");
        }
        return transitionTime;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    public String getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public SecretPurpose getPurpose() {
        return new SecretPurpose(purpose);
    }

    public long getBindingVersion() {
        return bindingVersion;
    }

    public LoginConnectionSecretStatus getStatus() {
        return status;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "LoginConnectionSecretVersion[id=" + id
                + ", organizationId=" + organizationId
                + ", connectionId=" + connectionId
                + ", purpose=" + purpose
                + ", bindingVersion=" + bindingVersion
                + ", status=" + status
                + ", envelope=<redacted>]";
    }
}
