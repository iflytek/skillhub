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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Organization-owned domain claim used only after explicit ownership verification. */
@Entity
@Table(name = "organization_domain")
public class OrganizationDomain {

    private static final Pattern LABEL_PATTERN =
            Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern IPV4_LIKE_PATTERN =
            Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "organization_id", nullable = false, length = 64)
    private String organizationId;

    @Column(nullable = false, length = 253)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationDomainStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", length = 64)
    private OrganizationDomainVerificationMethod verificationMethod;

    @Column(name = "verification_token_hash", length = 255)
    private String verificationTokenHash;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrganizationDomain() {
    }

    private OrganizationDomain(
            String organizationId,
            String domain,
            OrganizationDomainVerificationMethod verificationMethod,
            String challengeDigest,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID().toString();
        this.organizationId = requireNonBlank(organizationId, "organizationId");
        this.domain = normalize(domain);
        this.verificationMethod = Objects.requireNonNull(
                verificationMethod,
                "verificationMethod"
        );
        this.verificationTokenHash = requireNonBlank(challengeDigest, "challengeDigest");
        this.status = OrganizationDomainStatus.PENDING;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public static OrganizationDomain claim(
            String organizationId,
            String domain,
            OrganizationDomainVerificationMethod verificationMethod,
            String challengeDigest,
            Instant createdAt
    ) {
        return new OrganizationDomain(
                organizationId,
                domain,
                verificationMethod,
                challengeDigest,
                createdAt
        );
    }

    /** Normalizes and validates the exact host name accepted for ownership claims. */
    public static String normalize(String value) {
        if (value == null) {
            throw invalidDomain();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.length() > 253
                || IPV4_LIKE_PATTERN.matcher(normalized).matches()) {
            throw invalidDomain();
        }
        String[] labels = normalized.split("\\.", -1);
        for (String label : labels) {
            if (!LABEL_PATTERN.matcher(label).matches()) {
                throw invalidDomain();
            }
        }
        return normalized;
    }

    public boolean verify(Instant occurredAt) {
        if (status == OrganizationDomainStatus.VERIFIED) {
            return false;
        }
        if (status != OrganizationDomainStatus.PENDING) {
            throw invalidTransition();
        }
        Instant changeTime = requireCurrentOrLater(occurredAt);
        status = OrganizationDomainStatus.VERIFIED;
        verificationTokenHash = null;
        verifiedAt = changeTime;
        lastCheckedAt = changeTime;
        updatedAt = changeTime;
        return true;
    }

    public boolean disable(Instant occurredAt) {
        if (status == OrganizationDomainStatus.DISABLED) {
            return false;
        }
        Instant changeTime = requireCurrentOrLater(occurredAt);
        status = OrganizationDomainStatus.DISABLED;
        verificationTokenHash = null;
        updatedAt = changeTime;
        return true;
    }

    public void reissueChallenge(
            OrganizationDomainVerificationMethod method,
            String challengeDigest,
            Instant occurredAt
    ) {
        if (status == OrganizationDomainStatus.VERIFIED) {
            throw invalidTransition();
        }
        Instant changeTime = requireCurrentOrLater(occurredAt);
        status = OrganizationDomainStatus.PENDING;
        verificationMethod = Objects.requireNonNull(method, "verificationMethod");
        verificationTokenHash = requireNonBlank(challengeDigest, "challengeDigest");
        verifiedAt = null;
        lastCheckedAt = null;
        updatedAt = changeTime;
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

    String challengeDigest() {
        return verificationTokenHash;
    }

    private Instant requireCurrentOrLater(Instant occurredAt) {
        Instant changeTime = Objects.requireNonNull(occurredAt, "occurredAt");
        if (changeTime.isBefore(updatedAt)) {
            throw new DomainBadRequestException(
                    "error.organization.domain.transition.stale"
            );
        }
        return changeTime;
    }

    private DomainBadRequestException invalidTransition() {
        return new DomainBadRequestException(
                "error.organization.domain.transition.invalid"
        );
    }

    private static DomainBadRequestException invalidDomain() {
        return new DomainBadRequestException("error.organization.domain.invalid");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException(
                    "error.organization.domain.field.required",
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

    public String getDomain() {
        return domain;
    }

    public OrganizationDomainStatus getStatus() {
        return status;
    }

    public OrganizationDomainVerificationMethod getVerificationMethod() {
        return verificationMethod;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
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
