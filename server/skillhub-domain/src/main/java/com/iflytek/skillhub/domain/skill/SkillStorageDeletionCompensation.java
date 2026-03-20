package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_storage_delete_compensation")
public class SkillStorageDeletionCompensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(nullable = false, length = 128)
    private String namespace;

    @Column(nullable = false, length = 128)
    private String slug;

    @Column(name = "storage_keys_json", nullable = false, columnDefinition = "TEXT")
    private String storageKeysJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillStorageDeletionCompensationStatus status = SkillStorageDeletionCompensationStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillStorageDeletionCompensation() {
    }

    public SkillStorageDeletionCompensation(Long skillId,
                                            String namespace,
                                            String slug,
                                            String storageKeysJson,
                                            String lastError) {
        this.skillId = skillId;
        this.namespace = namespace;
        this.slug = slug;
        this.storageKeysJson = storageKeysJson;
        this.lastError = lastError;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getSlug() {
        return slug;
    }

    public String getStorageKeysJson() {
        return storageKeysJson;
    }

    public SkillStorageDeletionCompensationStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markAttempt(String error) {
        attemptCount += 1;
        lastAttemptAt = Instant.now(Clock.systemUTC());
        lastError = error;
        updatedAt = lastAttemptAt;
    }

    public void markCompleted() {
        status = SkillStorageDeletionCompensationStatus.COMPLETED;
        updatedAt = Instant.now(Clock.systemUTC());
    }
}
