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

/** Membership aggregate kept separate from the globally login-capable platform account. */
@Entity
@Table(name = "organization_membership")
public class OrganizationMembership {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", nullable = false, length = 64)
    private String organizationId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationMembershipStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private MembershipSourceType sourceType;

    @Column(name = "source_id", length = 256)
    private String sourceId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "primary_email", length = 256)
    private String primaryEmail;

    @Column(length = 256)
    private String department;

    @Column(name = "employee_number", length = 128)
    private String employeeNumber;

    @Column(name = "authority_version", nullable = false)
    private long authorityVersion;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "deprovisioned_at")
    private Instant deprovisionedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrganizationMembership() {
    }

    private OrganizationMembership(
            String organizationId,
            OrganizationMembershipStatus status,
            MembershipSourceType sourceType,
            String sourceId,
            String displayName,
            String primaryEmail,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = requireNonBlank(organizationId, "organizationId");
        this.status = Objects.requireNonNull(status, "status");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.sourceId = optionalText(sourceId);
        this.displayName = optionalText(displayName);
        this.primaryEmail = optionalText(primaryEmail);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static OrganizationMembership provisioned(
            String organizationId,
            MembershipSourceType sourceType,
            String sourceId,
            String displayName,
            String primaryEmail,
            Instant createdAt
    ) {
        return new OrganizationMembership(
                organizationId,
                OrganizationMembershipStatus.PROVISIONED,
                sourceType,
                sourceId,
                displayName,
                primaryEmail,
                createdAt
        );
    }

    public static OrganizationMembership invited(
            String organizationId,
            String invitationId,
            String displayName,
            String primaryEmail,
            Instant createdAt
    ) {
        return new OrganizationMembership(
                organizationId,
                OrganizationMembershipStatus.INVITED,
                MembershipSourceType.INVITATION,
                invitationId,
                displayName,
                primaryEmail,
                createdAt
        );
    }

    public void activate(String platformUserId, Instant occurredAt) {
        String requestedUserId = requireNonBlank(platformUserId, "userId");
        if (status == OrganizationMembershipStatus.ACTIVE) {
            if (!requestedUserId.equals(userId)) {
                throw invalidTransition(OrganizationMembershipStatus.ACTIVE);
            }
            return;
        }
        if (status != OrganizationMembershipStatus.PROVISIONED
                && status != OrganizationMembershipStatus.INVITED
                && status != OrganizationMembershipStatus.SUSPENDED) {
            throw invalidTransition(OrganizationMembershipStatus.ACTIVE);
        }
        if (userId != null && !requestedUserId.equals(userId)) {
            throw invalidTransition(OrganizationMembershipStatus.ACTIVE);
        }
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        userId = requestedUserId;
        status = OrganizationMembershipStatus.ACTIVE;
        activatedAt = transitionTime;
        suspendedAt = null;
        authorityVersion++;
        updatedAt = transitionTime;
    }

    public void suspend(Instant occurredAt) {
        if (status == OrganizationMembershipStatus.SUSPENDED) {
            return;
        }
        if (status != OrganizationMembershipStatus.ACTIVE) {
            throw invalidTransition(OrganizationMembershipStatus.SUSPENDED);
        }
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        status = OrganizationMembershipStatus.SUSPENDED;
        suspendedAt = transitionTime;
        authorityVersion++;
        updatedAt = transitionTime;
    }

    public void deprovision(Instant occurredAt) {
        if (status == OrganizationMembershipStatus.DEPROVISIONED) {
            return;
        }
        Instant transitionTime = requireCurrentOrLater(occurredAt);
        status = OrganizationMembershipStatus.DEPROVISIONED;
        deprovisionedAt = transitionTime;
        authorityVersion++;
        updatedAt = transitionTime;
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

    private Instant requireCurrentOrLater(Instant occurredAt) {
        Instant transitionTime = Objects.requireNonNull(occurredAt, "occurredAt");
        if (transitionTime.isBefore(updatedAt)) {
            throw new DomainBadRequestException("error.organization.membership.transition.stale");
        }
        return transitionTime;
    }

    private DomainBadRequestException invalidTransition(OrganizationMembershipStatus target) {
        return new DomainBadRequestException(
                "error.organization.membership.transition.invalid",
                status,
                target
        );
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException("error.organization.membership.field.required", field);
        }
        return value;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    public OrganizationMembershipStatus getStatus() {
        return status;
    }

    public MembershipSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrimaryEmail() {
        return primaryEmail;
    }

    public String getDepartment() {
        return department;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public long getAuthorityVersion() {
        return authorityVersion;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public Instant getDeprovisionedAt() {
        return deprovisionedAt;
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
