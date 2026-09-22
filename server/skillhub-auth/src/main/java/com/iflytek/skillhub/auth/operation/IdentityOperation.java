package com.iflytek.skillhub.auth.operation;

import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestFailureReason;
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

/** Correlated identity control-plane operation containing only bounded, non-secret diagnostics. */
@Entity
@Table(name = "identity_operation")
public class IdentityOperation {

    public static final String LOGIN_CONNECTION_TEST = "LOGIN_CONNECTION_TEST";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", length = 64)
    private String organizationId;

    @Column(name = "connection_id", length = 64)
    private String connectionId;

    @Column(name = "operation_type", nullable = false, length = 128)
    private String operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdentityOperationStatus status;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "external_request_id", length = 256)
    private String externalRequestId;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected IdentityOperation() {
    }

    private IdentityOperation(
            String organizationId,
            String connectionId,
            String revisionId,
            IdentityOperationStatus status,
            String requestId,
            String errorCode,
            Instant occurredAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = requireText(organizationId, "organizationId");
        this.connectionId = requireText(connectionId, "connectionId");
        this.operationType = LOGIN_CONNECTION_TEST;
        this.externalRequestId = requireText(revisionId, "revisionId");
        this.status = Objects.requireNonNull(status, "status");
        this.requestId = normalizeNullable(requestId);
        this.errorCode = normalizeNullable(errorCode);
        this.startedAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.completedAt = occurredAt;
        this.createdAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    public static IdentityOperation successfulLoginConnectionTest(
            String organizationId,
            String connectionId,
            String revisionId,
            String requestId,
            Instant occurredAt
    ) {
        return new IdentityOperation(
                organizationId,
                connectionId,
                revisionId,
                IdentityOperationStatus.SUCCEEDED,
                requestId,
                null,
                occurredAt
        );
    }

    public static IdentityOperation failedLoginConnectionTest(
            String organizationId,
            String connectionId,
            String revisionId,
            String requestId,
            LoginConnectionTestFailureReason reason,
            Instant occurredAt
    ) {
        return new IdentityOperation(
                organizationId,
                connectionId,
                revisionId,
                IdentityOperationStatus.FAILED,
                requestId,
                Objects.requireNonNull(reason, "reason").name(),
                occurredAt
        );
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public String getOperationType() {
        return operationType;
    }

    public IdentityOperationStatus getStatus() {
        return status;
    }

    public Optional<String> getRequestId() {
        return Optional.ofNullable(requestId);
    }

    public String getRevisionId() {
        return externalRequestId;
    }

    public Optional<String> getErrorCode() {
        return Optional.ofNullable(errorCode);
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
