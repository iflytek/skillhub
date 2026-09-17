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

/** Trusted immutable subject coordinate prepared before an employee's first enterprise login. */
@Entity
@Table(name = "preprovisioned_login_subject")
public class PreProvisionedLoginSubject {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", nullable = false, length = 64, updatable = false)
    private String organizationId;

    @Column(name = "membership_id", nullable = false, length = 64, updatable = false)
    private String membershipId;

    @Column(name = "login_connection_id", nullable = false, length = 64, updatable = false)
    private String loginConnectionId;

    @Column(nullable = false, length = 512, updatable = false)
    private String issuer;

    @Column(name = "subject_type", nullable = false, length = 64, updatable = false)
    private String subjectType;

    @Column(name = "subject_value", nullable = false, length = 1024, updatable = false)
    private String subjectValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PreProvisionedLoginSubjectStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PreProvisionedLoginSubject() {
    }

    private PreProvisionedLoginSubject(
            String organizationId,
            String membershipId,
            ExternalIdentityCoordinate coordinate,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = bounded(organizationId, 64, "organizationId");
        this.membershipId = bounded(membershipId, 64, "membershipId");
        this.loginConnectionId = bounded(coordinate.connectionId(), 64, "connectionId");
        this.issuer = bounded(coordinate.issuer().toString(), 512, "issuer");
        this.subjectType = bounded(coordinate.subject().type().value(), 64, "subjectType");
        this.subjectValue = boundedOpaque(
                coordinate.subject().value(),
                1024,
                "subjectValue"
        );
        this.status = PreProvisionedLoginSubjectStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static PreProvisionedLoginSubject claim(
            String organizationId,
            String membershipId,
            ExternalIdentityCoordinate coordinate,
            Instant createdAt
    ) {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        String coordinateOrganization = coordinate.organizationId().orElseThrow(
                () -> new IllegalArgumentException(
                        "pre-provisioned login subject requires an organization"
                )
        );
        if (!coordinateOrganization.equals(organizationId)) {
            throw new IllegalArgumentException("coordinate organization does not match claim");
        }
        return new PreProvisionedLoginSubject(
                organizationId,
                membershipId,
                coordinate,
                createdAt
        );
    }

    public ExternalIdentityCoordinate coordinate() {
        return new ExternalIdentityCoordinate(
                Optional.of(organizationId),
                loginConnectionId,
                URI.create(issuer),
                new SubjectRef(
                        new SubjectType(subjectType),
                        subjectValue
                )
        );
    }

    public boolean isActive() {
        return status == PreProvisionedLoginSubjectStatus.ACTIVE;
    }

    public void revoke(Instant occurredAt) {
        if (status == PreProvisionedLoginSubjectStatus.REVOKED) {
            return;
        }
        Instant transitionTime = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (transitionTime.isBefore(updatedAt)) {
            throw new IllegalArgumentException("revocation time must not precede the last update");
        }
        status = PreProvisionedLoginSubjectStatus.REVOKED;
        revokedAt = transitionTime;
        updatedAt = transitionTime;
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

    public String getMembershipId() {
        return membershipId;
    }

    public String getLoginConnectionId() {
        return loginConnectionId;
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
}
