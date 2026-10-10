package com.iflytek.skillhub.domain.authoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

/**
 * One file inside a skill draft. File bodies live in object storage under a
 * content-addressed key; this record tracks identity, integrity digest, and size.
 */
@Entity
@Table(name = "draft_file",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uq_draft_file_path", columnNames = {"draft_id", "file_path"}))
public class DraftFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false)
    private Long draftId;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private Long size;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "storage_key", nullable = false, length = 768)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DraftFile() {
    }

    public DraftFile(Long draftId, String filePath, String sha256, Long size,
                     String contentType, String storageKey) {
        this.draftId = draftId;
        this.filePath = filePath;
        this.sha256 = sha256;
        this.size = size;
        this.contentType = contentType;
        this.storageKey = storageKey;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public void updateContent(String sha256, Long size, String contentType, String storageKey) {
        this.sha256 = sha256;
        this.size = size;
        this.contentType = contentType;
        this.storageKey = storageKey;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public Long getDraftId() {
        return draftId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSha256() {
        return sha256;
    }

    public Long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
