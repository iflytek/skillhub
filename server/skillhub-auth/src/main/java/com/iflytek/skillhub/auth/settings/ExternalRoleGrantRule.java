package com.iflytek.skillhub.auth.settings;

import com.iflytek.skillhub.auth.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "external_role_grant_rule")
public class ExternalRoleGrantRule {
    public enum Status { ACTIVE, DISABLED, CONSUMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "normalized_email", nullable = false, length = 256)
    private String normalizedEmail;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "matched_subject", length = 256)
    private String matchedSubject;

    @Column(name = "granted_user_id", length = 128)
    private String grantedUserId;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExternalRoleGrantRule() {}

    public ExternalRoleGrantRule(String providerCode, String normalizedEmail, Role role, String actorUserId) {
        this.providerCode = providerCode;
        this.normalizedEmail = normalizedEmail;
        this.role = role;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getProviderCode() { return providerCode; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public String getMatchedSubject() { return matchedSubject; }
    public String getGrantedUserId() { return grantedUserId; }
    public Instant getGrantedAt() { return grantedAt; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(Role nextRole, String actorUserId) {
        if (status != Status.ACTIVE) throw new IllegalStateException("Only active rules may be edited");
        this.role = nextRole;
        this.updatedBy = actorUserId;
    }

    public void disable(String actorUserId) {
        if (status != Status.ACTIVE) throw new IllegalStateException("Only active rules may be disabled");
        this.status = Status.DISABLED;
        this.updatedBy = actorUserId;
    }

    public void consume(String subject, String userId) {
        if (status != Status.ACTIVE) throw new IllegalStateException("Only active rules may be consumed");
        if (subject == null || subject.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Consumed rule requires an external subject and user ID");
        }
        this.status = Status.CONSUMED;
        this.matchedSubject = subject;
        this.grantedUserId = userId;
        this.grantedAt = Instant.now();
    }
}
