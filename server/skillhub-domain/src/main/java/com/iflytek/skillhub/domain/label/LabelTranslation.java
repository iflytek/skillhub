package com.iflytek.skillhub.domain.label;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "label_translation")
public class LabelTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label_id", nullable = false)
    private Long labelId;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LabelTranslation() {
    }

    public LabelTranslation(Long labelId, String locale, String displayName) {
        this.labelId = labelId;
        this.locale = locale;
        this.displayName = displayName;
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

    public Long getLabelId() {
        return labelId;
    }

    public String getLocale() {
        return locale;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
