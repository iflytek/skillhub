package com.iflytek.skillhub.infra.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_search_document")
public class SkillSearchDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false, unique = true)
    private Long skillId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "namespace_slug", nullable = false, length = 64)
    private String namespaceSlug;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(length = 256)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "semantic_vector", columnDefinition = "TEXT")
    private String semanticVector;

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SkillSearchDocumentEntity() {
    }

    public SkillSearchDocumentEntity(
            Long skillId,
            Long namespaceId,
            String namespaceSlug,
            String ownerId,
            String title,
            String summary,
            String keywords,
            String searchText,
            String semanticVector,
            String visibility,
            String status) {
        this.skillId = skillId;
        this.namespaceId = namespaceId;
        this.namespaceSlug = namespaceSlug;
        this.ownerId = ownerId;
        this.title = title;
        this.summary = summary;
        this.keywords = keywords;
        this.searchText = searchText;
        this.semanticVector = semanticVector;
        this.visibility = visibility;
        this.status = status;
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public String getNamespaceSlug() {
        return namespaceSlug;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getSearchText() {
        return searchText;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getSemanticVector() {
        return semanticVector;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setNamespaceId(Long namespaceId) {
        this.namespaceId = namespaceId;
    }

    public void setNamespaceSlug(String namespaceSlug) {
        this.namespaceSlug = namespaceSlug;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public void setSemanticVector(String semanticVector) {
        this.semanticVector = semanticVector;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
