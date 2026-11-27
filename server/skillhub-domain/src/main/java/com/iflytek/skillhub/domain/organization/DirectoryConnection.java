package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Organization-scoped inbound provisioning connection. */
@Entity
@Table(name = "directory_connection")
public class DirectoryConnection {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", nullable = false, length = 64)
    private String organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DirectoryConnectionProtocol protocol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DirectoryConnectionStatus status;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "secret_version", nullable = false)
    private long secretVersion;

    @Column(name = "token_hint", nullable = false, length = 16)
    private String tokenHint;

    @Column(name = "authority_version", nullable = false)
    private long authorityVersion;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected DirectoryConnection() {
    }

    private DirectoryConnection(
            String organizationId,
            String createdBy,
            String secretHash,
            String tokenHint,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = requireNonBlank(organizationId, "organizationId");
        this.createdBy = requireNonBlank(createdBy, "createdBy");
        this.protocol = DirectoryConnectionProtocol.SCIM_2_0;
        this.status = DirectoryConnectionStatus.DRAFT;
        this.secretHash = requireNonBlank(secretHash, "secretHash");
        this.secretVersion = 1;
        this.tokenHint = requireNonBlank(tokenHint, "tokenHint");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static DirectoryConnection create(
            String organizationId,
            String createdBy,
            String secretHash,
            String tokenHint,
            Instant createdAt
    ) {
        return new DirectoryConnection(organizationId, createdBy, secretHash, tokenHint, createdAt);
    }

    public void activate(Instant occurredAt) {
        transition(DirectoryConnectionStatus.ACTIVE, occurredAt);
    }

    public void suspend(Instant occurredAt) {
        transition(DirectoryConnectionStatus.SUSPENDED, occurredAt);
    }

    public void disable(Instant occurredAt) {
        if (status == DirectoryConnectionStatus.DISABLED) {
            return;
        }
        Instant at = requireCurrentOrLater(occurredAt);
        status = DirectoryConnectionStatus.DISABLED;
        authorityVersion++;
        updatedAt = at;
    }

    public void rotateSecret(String secretHash, String tokenHint, Instant occurredAt) {
        Instant at = requireCurrentOrLater(occurredAt);
        this.secretHash = requireNonBlank(secretHash, "secretHash");
        this.tokenHint = requireNonBlank(tokenHint, "tokenHint");
        this.secretVersion++;
        this.authorityVersion++;
        this.updatedAt = at;
    }

    public boolean acceptsSecret(String candidateHash) {
        return status == DirectoryConnectionStatus.ACTIVE
                && secretHash.equals(candidateHash);
    }

    private void transition(DirectoryConnectionStatus target, Instant occurredAt) {
        if (status == target) {
            return;
        }
        if (target == DirectoryConnectionStatus.ACTIVE
                && status != DirectoryConnectionStatus.DRAFT
                && status != DirectoryConnectionStatus.SUSPENDED) {
            throw invalidTransition(target);
        }
        if (target == DirectoryConnectionStatus.SUSPENDED
                && status != DirectoryConnectionStatus.ACTIVE) {
            throw invalidTransition(target);
        }
        Instant at = requireCurrentOrLater(occurredAt);
        status = target;
        authorityVersion++;
        updatedAt = at;
    }

    private Instant requireCurrentOrLater(Instant occurredAt) {
        Instant at = Objects.requireNonNull(occurredAt, "occurredAt");
        if (at.isBefore(updatedAt)) {
            throw new DomainBadRequestException("error.directory.connection.transition.stale");
        }
        return at;
    }

    private DomainBadRequestException invalidTransition(DirectoryConnectionStatus target) {
        return new DomainBadRequestException(
                "error.directory.connection.transition.invalid", status, target);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException("error.directory.connection.field.required", field);
        }
        return value.trim();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now(Clock.systemUTC());
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public DirectoryConnectionProtocol getProtocol() { return protocol; }
    public DirectoryConnectionStatus getStatus() { return status; }
    public String getSecretHash() { return secretHash; }
    public long getSecretVersion() { return secretVersion; }
    public String getTokenHint() { return tokenHint; }
    public long getAuthorityVersion() { return authorityVersion; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
