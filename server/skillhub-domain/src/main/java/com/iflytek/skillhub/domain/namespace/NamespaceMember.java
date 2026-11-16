package com.iflytek.skillhub.domain.namespace;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "namespace_member",
       uniqueConstraints = @UniqueConstraint(columnNames = {"namespace_id", "user_id"}))
public class NamespaceMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NamespaceRole role;

    @Column(name = "team_auto_publication_grant_id")
    private UUID teamAutoPublicationGrantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NamespaceMember() {}

    public NamespaceMember(Long namespaceId, String userId, NamespaceRole role) {
        this.namespaceId = namespaceId;
        this.userId = userId;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public void setNamespaceId(Long namespaceId) { this.namespaceId = namespaceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public UUID getTeamAutoPublicationGrantId() { return teamAutoPublicationGrantId; }
    public boolean isTeamAutoPublicationGranted() { return teamAutoPublicationGrantId != null; }
    public UUID grantTeamAutoPublication() {
        if (teamAutoPublicationGrantId == null) {
            teamAutoPublicationGrantId = UUID.randomUUID();
        }
        return teamAutoPublicationGrantId;
    }
    public UUID revokeTeamAutoPublication() {
        UUID previous = teamAutoPublicationGrantId;
        teamAutoPublicationGrantId = null;
        return previous;
    }
    public NamespaceRole getRole() { return role; }
    public void setRole(NamespaceRole role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
