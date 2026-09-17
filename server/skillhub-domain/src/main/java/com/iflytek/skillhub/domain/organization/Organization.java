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
import java.util.regex.Pattern;

/** Enterprise tenant aggregate whose lifecycle gates every organization-scoped capability. */
@Entity
@Table(name = "organization")
public class Organization {

    private static final Pattern SLUG_PATTERN =
            Pattern.compile("[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){1,63}");

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationStatus status;

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

    protected Organization() {
    }

    private Organization(
            String id,
            String slug,
            String displayName,
            String createdBy,
            Instant createdAt
    ) {
        this.id = requireNonBlank(id, "id");
        this.slug = requireSlug(slug);
        this.displayName = requireNonBlank(displayName, "displayName");
        this.createdBy = requireNonBlank(createdBy, "createdBy");
        this.status = OrganizationStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static Organization create(
            String slug,
            String displayName,
            String createdBy,
            Instant createdAt
    ) {
        return new Organization(
                UUID.randomUUID().toString(),
                slug,
                displayName,
                createdBy,
                createdAt
        );
    }

    public void suspend(Instant occurredAt) {
        transition(OrganizationStatus.ACTIVE, OrganizationStatus.SUSPENDED, occurredAt);
    }

    public void reactivate(Instant occurredAt) {
        transition(OrganizationStatus.SUSPENDED, OrganizationStatus.ACTIVE, occurredAt);
    }

    public void decommission(Instant occurredAt) {
        transition(OrganizationStatus.SUSPENDED, OrganizationStatus.DECOMMISSIONED, occurredAt);
    }

    /** Marks an effective Organization RBAC change so existing authorization becomes stale. */
    public void recordRoleBindingChange(Instant occurredAt) {
        Instant changeTime = Objects.requireNonNull(occurredAt, "occurredAt");
        if (changeTime.isBefore(updatedAt)) {
            throw new DomainBadRequestException("error.organization.state.transition.stale");
        }
        authorityVersion++;
        updatedAt = changeTime;
    }

    private void transition(
            OrganizationStatus requiredCurrent,
            OrganizationStatus target,
            Instant occurredAt
    ) {
        if (status == target) {
            return;
        }
        if (status != requiredCurrent) {
            throw new DomainBadRequestException(
                    "error.organization.state.transition.invalid",
                    status,
                    target
            );
        }
        Instant transitionTime = Objects.requireNonNull(occurredAt, "occurredAt");
        if (transitionTime.isBefore(updatedAt)) {
            throw new DomainBadRequestException("error.organization.state.transition.stale");
        }
        status = target;
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

    private static String requireSlug(String value) {
        String slug = requireNonBlank(value, "slug");
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new DomainBadRequestException("error.organization.slug.invalid");
        }
        return slug;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException("error.organization.field.required", field);
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public long getAuthorityVersion() {
        return authorityVersion;
    }

    public String getCreatedBy() {
        return createdBy;
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
