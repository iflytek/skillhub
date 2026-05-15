package com.iflytek.skillhub.domain.promotion;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents a configured promotion placement (e.g., HOME_HERO, SEARCH_PINNED).
 * The slot bounds operational rules: which target types may appear, the maximum
 * number of concurrently active campaigns, and whether the placement is enabled.
 */
@Entity
@Table(name = "promotion_slot")
public class PromotionSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_code", nullable = false, unique = true, length = 64)
    private String slotCode;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "target_types", nullable = false, columnDefinition = "jsonb")
    private String targetTypesJson = "[]";

    @Column(name = "max_active_items", nullable = false)
    private int maxActiveItems = 5;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PromotionSlot() {}

    public PromotionSlot(String slotCode, String displayName, String targetTypesJson, int maxActiveItems) {
        this.slotCode = slotCode;
        this.displayName = displayName;
        this.targetTypesJson = targetTypesJson;
        this.maxActiveItems = maxActiveItems;
    }

    public Long getId() { return id; }
    public String getSlotCode() { return slotCode; }
    public String getDisplayName() { return displayName; }
    public String getTargetTypesJson() { return targetTypesJson; }
    public int getMaxActiveItems() { return maxActiveItems; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setMaxActiveItems(int maxActiveItems) { this.maxActiveItems = maxActiveItems; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
