package com.iflytek.skillhub.auth.federation.association;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import com.iflytek.skillhub.auth.federation.core.SubjectRef;
import com.iflytek.skillhub.auth.federation.core.SubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent binding from a verified external identity coordinate to one Platform Account. */
@Entity
@Table(name = "external_identity")
public class ExternalIdentity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", length = 64, updatable = false)
    private String organizationId;

    @Column(name = "connection_id", nullable = false, length = 64, updatable = false)
    private String connectionId;

    @Column(nullable = false, length = 512, updatable = false)
    private String issuer;

    @Column(name = "subject_type", nullable = false, length = 64, updatable = false)
    private String subjectType;

    @Column(name = "subject_value", nullable = false, length = 1024, updatable = false)
    private String subjectValue;

    @Column(name = "user_id", nullable = false, length = 128, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExternalIdentityStatus status;

    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ExternalIdentity() {
    }

    private ExternalIdentity(
            ExternalIdentityCoordinate coordinate,
            String userId,
            Instant authenticatedAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = coordinate.organizationId().orElse(null);
        this.connectionId = bounded(coordinate.connectionId(), 64, "connectionId");
        this.issuer = bounded(coordinate.issuer().toString(), 512, "issuer");
        this.subjectType = bounded(coordinate.subject().type().value(), 64, "subjectType");
        this.subjectValue = boundedOpaque(
                coordinate.subject().value(),
                1024,
                "subjectValue"
        );
        this.userId = bounded(userId, 128, "userId");
        this.status = ExternalIdentityStatus.ACTIVE;
        this.lastAuthenticatedAt = Objects.requireNonNull(
                authenticatedAt,
                "authenticatedAt must not be null"
        );
        this.createdAt = authenticatedAt;
        this.updatedAt = authenticatedAt;
    }

    public static ExternalIdentity bind(
            ExternalIdentityCoordinate coordinate,
            String userId,
            Instant authenticatedAt
    ) {
        return new ExternalIdentity(
                Objects.requireNonNull(coordinate, "coordinate must not be null"),
                userId,
                authenticatedAt
        );
    }

    public ExternalIdentityCoordinate coordinate() {
        return new ExternalIdentityCoordinate(
                Optional.ofNullable(organizationId),
                connectionId,
                URI.create(issuer),
                new SubjectRef(
                        new SubjectType(subjectType),
                        subjectValue
                )
        );
    }

    public void recordAuthentication(Instant authenticatedAt) {
        requireActive();
        Instant observedAt = Objects.requireNonNull(
                authenticatedAt,
                "authenticatedAt must not be null"
        );
        if (observedAt.isAfter(lastAuthenticatedAt)) {
            lastAuthenticatedAt = observedAt;
            updatedAt = observedAt;
        }
    }

    public void requireActive() {
        if (status != ExternalIdentityStatus.ACTIVE) {
            throw new EnterpriseIdentityAssociationException(
                    EnterpriseIdentityAssociationFailure.BINDING_UNAVAILABLE
            );
        }
    }

    private static String bounded(String value, int maximum, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " is outside its persistence bounds");
        }
        return normalized;
    }

    private static String boundedOpaque(String value, int maximum, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is outside its persistence bounds");
        }
        return value;
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

    public String getIssuer() {
        return issuer;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public String getSubjectValue() {
        return subjectValue;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isActive() {
        return status == ExternalIdentityStatus.ACTIVE;
    }

    public Instant getLastAuthenticatedAt() {
        return lastAuthenticatedAt;
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
