package com.iflytek.skillhub.domain.label;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "label_definition")
public class LabelDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, unique = true, length = 64)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private LabelType type;

    @Column(name = "visible_in_filter", nullable = false)
    private boolean visibleInFilter = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LabelDefinition() {
    }

    public LabelDefinition(String slug, LabelType type, boolean visibleInFilter, int sortOrder, String createdBy) {
        this.slug = slug;
        this.type = type;
        this.visibleInFilter = visibleInFilter;
        this.sortOrder = sortOrder;
        this.createdBy = createdBy;
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

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public LabelType getType() {
        return type;
    }

    public boolean isVisibleInFilter() {
        return visibleInFilter;
    }

    public int getSortOrder() {
        return sortOrder;
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

    public void setType(LabelType type) {
        this.type = type;
    }

    public void setVisibleInFilter(boolean visibleInFilter) {
        this.visibleInFilter = visibleInFilter;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
