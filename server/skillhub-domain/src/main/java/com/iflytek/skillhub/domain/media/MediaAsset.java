package com.iflytek.skillhub.domain.media;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted media attachment — typically a GIF demo, a static cover, or a screenshot.
 *
 * <p>The asset is stored verbatim in object storage at {@link #getObjectKey()}; the
 * {@code /api/v1/media/{id}} endpoint streams it back with the recorded
 * {@link #getContentType()} and length so caches can range-fetch.
 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 64)
    private MediaOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaAssetRole role;

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "alt_text", length = 256)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MediaAsset() {}

    public MediaAsset(MediaOwnerType ownerType, Long ownerId,
                      MediaType mediaType, MediaAssetRole role,
                      String objectKey, String contentType,
                      long sizeBytes, String sha256, String createdBy) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.mediaType = mediaType;
        this.role = role;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public MediaOwnerType getOwnerType() { return ownerType; }
    public Long getOwnerId() { return ownerId; }
    public MediaType getMediaType() { return mediaType; }
    public MediaAssetRole getRole() { return role; }
    public String getFilePath() { return filePath; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getAltText() { return altText; }
    public int getSortOrder() { return sortOrder; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setAltText(String altText) { this.altText = altText; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
