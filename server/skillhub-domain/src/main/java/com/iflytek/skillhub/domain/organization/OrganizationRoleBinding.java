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

/** Auditable assignment of one administrative role within one Organization. */
@Entity
@Table(name = "organization_role_binding")
public class OrganizationRoleBinding {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", nullable = false, length = 64)
    private String organizationId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationRoleBindingStatus status;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "revoked_by", length = 128)
    private String revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrganizationRoleBinding() {
    }

    private OrganizationRoleBinding(
            String organizationId,
            String userId,
            OrganizationRole role,
            String createdBy,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = requireNonBlank(organizationId, "organizationId");
        this.userId = requireNonBlank(userId, "userId");
        this.role = Objects.requireNonNull(role, "role");
        this.status = OrganizationRoleBindingStatus.ACTIVE;
        this.createdBy = requireNonBlank(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static OrganizationRoleBinding grant(
            String organizationId,
            String userId,
            OrganizationRole role,
            String createdBy,
            Instant createdAt
    ) {
        return new OrganizationRoleBinding(
                organizationId,
                userId,
                role,
                createdBy,
                createdAt
        );
    }

    /** Returns true only when this call performs the terminal state transition. */
    public boolean revoke(String actorId, Instant occurredAt) {
        if (status == OrganizationRoleBindingStatus.REVOKED) {
            return false;
        }
        String revocationActor = requireNonBlank(actorId, "revokedBy");
        Instant transitionTime = Objects.requireNonNull(occurredAt, "occurredAt");
        if (transitionTime.isBefore(updatedAt)) {
            throw new DomainBadRequestException(
                    "error.organization.role-binding.transition.stale"
            );
        }
        status = OrganizationRoleBindingStatus.REVOKED;
        revokedBy = revocationActor;
        revokedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
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

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException(
                    "error.organization.role-binding.field.required",
                    field
            );
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getUserId() {
        return userId;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public OrganizationRoleBindingStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
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
