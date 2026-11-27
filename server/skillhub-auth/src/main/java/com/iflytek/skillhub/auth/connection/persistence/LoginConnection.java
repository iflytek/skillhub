package com.iflytek.skillhub.auth.connection.persistence;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
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

/** Persistent control-plane row for one platform or organization login connection. */
@Entity
@Table(name = "login_connection")
public class LoginConnection {

    public static final String OIDC_ADAPTER_KEY = "oidc-standard";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "public_handle", nullable = false, length = 128)
    private String publicHandle;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private LoginConnectionScopeType scopeType;

    @Column(name = "organization_id", length = 64)
    private String organizationId;

    @Column(name = "system_key", length = 128)
    private String systemKey;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LoginConnectionStatus status;

    @Column(name = "adapter_key", nullable = false, length = 128)
    private String adapterKey;

    @Column(name = "active_revision_id", length = 64)
    private String activeRevisionId;

    @Column(name = "last_tested_revision_id", length = 64)
    private String lastTestedRevisionId;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected LoginConnection() {
    }

    public static LoginConnection createOrganization(String organizationId, String displayName,
                                                       String actorId, Instant now) {
        LoginConnection connection = new LoginConnection();
        connection.id = UUID.randomUUID().toString();
        connection.publicHandle = UUID.randomUUID().toString();
        connection.scopeType = LoginConnectionScopeType.ORGANIZATION;
        connection.organizationId = Objects.requireNonNull(organizationId);
        connection.displayName = Objects.requireNonNull(displayName);
        connection.status = LoginConnectionStatus.DRAFT;
        connection.adapterKey = OIDC_ADAPTER_KEY;
        connection.createdBy = Objects.requireNonNull(actorId);
        connection.createdAt = Objects.requireNonNull(now);
        connection.updatedAt = now;
        return connection;
    }

    public void recordSuccessfulTest(String revisionId, Instant now) {
        requireMutable();
        lastTestedRevisionId = Objects.requireNonNull(revisionId);
        updatedAt = Objects.requireNonNull(now);
    }

    public void recordFailedTest(String revisionId, Instant now) {
        requireMutable();
        if (Objects.equals(lastTestedRevisionId, revisionId)) {
            lastTestedRevisionId = null;
        }
        updatedAt = Objects.requireNonNull(now);
    }

    public void activate(String revisionId, Instant now) {
        requireMutable();
        if (revisionId == null || !revisionId.equals(lastTestedRevisionId)) {
            throw new DomainConflictException("error.loginConnection.revision.notTested");
        }
        if (status != LoginConnectionStatus.ACTIVE || !revisionId.equals(activeRevisionId)) {
            activeRevisionId = revisionId;
            status = LoginConnectionStatus.ACTIVE;
            updatedAt = Objects.requireNonNull(now);
        }
    }

    public void suspend(Instant now) {
        requireMutable();
        if (status == LoginConnectionStatus.SUSPENDED) {
            return;
        }
        if (status != LoginConnectionStatus.ACTIVE) {
            throw new DomainConflictException("error.loginConnection.status.invalid");
        }
        status = LoginConnectionStatus.SUSPENDED;
        updatedAt = Objects.requireNonNull(now);
    }

    public boolean resume(String revisionId, Instant now) {
        if (status != LoginConnectionStatus.SUSPENDED && status != LoginConnectionStatus.ACTIVE) {
            throw new DomainConflictException("error.loginConnection.status.invalid");
        }
        if (activeRevisionId == null || !activeRevisionId.equals(revisionId)) {
            throw new DomainConflictException("error.loginConnection.revision.changed");
        }
        if (status == LoginConnectionStatus.ACTIVE) {
            return false;
        }
        status = LoginConnectionStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now);
        return true;
    }

    public void disable(Instant now) {
        if (status != LoginConnectionStatus.DISABLED) {
            status = LoginConnectionStatus.DISABLED;
            updatedAt = Objects.requireNonNull(now);
        }
    }

    private void requireMutable() {
        if (status == LoginConnectionStatus.DISABLED) {
            throw new DomainConflictException("error.loginConnection.status.invalid");
        }
    }

    public String getId() {
        return id;
    }

    public String getPublicHandle() {
        return publicHandle;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LoginConnectionStatus getStatus() {
        return status;
    }

    public String getAdapterKey() {
        return adapterKey;
    }

    public String getActiveRevisionId() {
        return activeRevisionId;
    }

    public String getLastTestedRevisionId() {
        return lastTestedRevisionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
