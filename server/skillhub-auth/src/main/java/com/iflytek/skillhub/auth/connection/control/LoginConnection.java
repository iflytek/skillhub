package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent control-plane envelope for a login connection. */
@Entity
@Table(name = "login_connection")
public class LoginConnection {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "public_handle", nullable = false, length = 128, unique = true)
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

    private LoginConnection(
            String id,
            ConnectionHandle publicHandle,
            LoginConnectionScopeType scopeType,
            String organizationId,
            String systemKey,
            String displayName,
            AdapterKey adapterKey,
            String createdBy,
            Instant createdAt
    ) {
        this.id = requireText(id, "id");
        this.publicHandle = Objects.requireNonNull(publicHandle, "publicHandle").value();
        this.scopeType = Objects.requireNonNull(scopeType, "scopeType");
        this.organizationId = normalizeNullableText(organizationId, "organizationId");
        this.systemKey = normalizeNullableText(systemKey, "systemKey");
        requireScopeAlignment();
        this.displayName = requireText(displayName, "displayName");
        this.adapterKey = Objects.requireNonNull(adapterKey, "adapterKey").value();
        this.createdBy = requireText(createdBy, "createdBy");
        this.status = LoginConnectionStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static LoginConnection createOrganization(
            String organizationId,
            String displayName,
            AdapterKey adapterKey,
            String createdBy,
            Instant createdAt
    ) {
        String id = UUID.randomUUID().toString();
        return new LoginConnection(
                id,
                new ConnectionHandle("login-" + UUID.randomUUID()),
                LoginConnectionScopeType.ORGANIZATION,
                organizationId,
                null,
                displayName,
                adapterKey,
                createdBy,
                createdAt
        );
    }

    public static LoginConnection createPlatform(
            String systemKey,
            String displayName,
            AdapterKey adapterKey,
            String createdBy,
            Instant createdAt
    ) {
        String id = UUID.randomUUID().toString();
        return new LoginConnection(
                id,
                new ConnectionHandle("login-" + UUID.randomUUID()),
                LoginConnectionScopeType.PLATFORM,
                null,
                requireText(systemKey, "systemKey"),
                displayName,
                adapterKey,
                createdBy,
                createdAt
        );
    }

    /** Records only a successful probe. A failed probe must not call this transition. */
    public void recordSuccessfulTest(String revisionId, Instant occurredAt) {
        requireNotDisabled();
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        String testedRevisionId = requireText(revisionId, "revisionId");
        if (testedRevisionId.equals(lastTestedRevisionId)) {
            return;
        }
        lastTestedRevisionId = testedRevisionId;
        updatedAt = transitionTime;
    }

    public void activate(String revisionId, Instant occurredAt) {
        requireNotDisabled();
        String targetRevisionId = requireText(revisionId, "revisionId");
        if (!targetRevisionId.equals(lastTestedRevisionId)) {
            throw new DomainBadRequestException("error.loginConnection.revision.notTested");
        }
        if (status == LoginConnectionStatus.ACTIVE
                && targetRevisionId.equals(activeRevisionId)) {
            return;
        }
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        status = LoginConnectionStatus.ACTIVE;
        activeRevisionId = targetRevisionId;
        updatedAt = transitionTime;
    }

    public void suspend(Instant occurredAt) {
        if (status == LoginConnectionStatus.SUSPENDED) {
            return;
        }
        if (status != LoginConnectionStatus.ACTIVE) {
            throw invalidTransition(LoginConnectionStatus.SUSPENDED);
        }
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        status = LoginConnectionStatus.SUSPENDED;
        updatedAt = transitionTime;
    }

    public void disable(Instant occurredAt) {
        if (status == LoginConnectionStatus.DISABLED) {
            return;
        }
        updatedAt = requireCurrentOrLater(occurredAt);
        status = LoginConnectionStatus.DISABLED;
    }

    /** Returns the immutable revision pointer allowed to enter the authentication data plane. */
    public String requireStartableRevisionId() {
        if (status != LoginConnectionStatus.ACTIVE || activeRevisionId == null) {
            throw new ConnectionUnavailableException();
        }
        return activeRevisionId;
    }

    private void requireNotDisabled() {
        if (status == LoginConnectionStatus.DISABLED) {
            throw new DomainBadRequestException("error.loginConnection.disabled");
        }
    }

    private Instant requireCurrentOrLater(Instant occurredAt) {
        Instant transitionTime = Objects.requireNonNull(occurredAt, "occurredAt");
        if (transitionTime.isBefore(updatedAt)) {
            throw new DomainBadRequestException("error.loginConnection.state.transition.stale");
        }
        return transitionTime;
    }

    private DomainBadRequestException invalidTransition(LoginConnectionStatus target) {
        return new DomainBadRequestException(
                "error.loginConnection.state.transition.invalid",
                status,
                target
        );
    }

    private void requireScopeAlignment() {
        boolean valid = switch (scopeType) {
            case PLATFORM -> organizationId == null;
            case ORGANIZATION -> organizationId != null && systemKey == null;
        };
        if (!valid) {
            throw new DomainBadRequestException("error.loginConnection.scope.invalid");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException("error.loginConnection.field.required", field);
        }
        return value.trim();
    }

    private static String normalizeNullableText(String value, String field) {
        return value == null ? null : requireText(value, field);
    }

    public String getId() {
        return id;
    }

    public LoginConnectionScopeType getScopeType() {
        return scopeType;
    }

    public ConnectionHandle getPublicHandle() {
        return new ConnectionHandle(publicHandle);
    }

    public Optional<String> getOrganizationId() {
        return Optional.ofNullable(organizationId);
    }

    public Optional<String> getSystemKey() {
        return Optional.ofNullable(systemKey);
    }

    public String getDisplayName() {
        return displayName;
    }

    public LoginConnectionStatus getStatus() {
        return status;
    }

    public AdapterKey getAdapterKey() {
        return new AdapterKey(adapterKey);
    }

    public Optional<String> getActiveRevisionId() {
        return Optional.ofNullable(activeRevisionId);
    }

    public Optional<String> getLastTestedRevisionId() {
        return Optional.ofNullable(lastTestedRevisionId);
    }

    public Optional<String> getCreatedBy() {
        return Optional.ofNullable(createdBy);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
