package com.iflytek.skillhub.domain.bundle;

import jakarta.persistence.*;

/**
 * One skill entry inside a bundle version. Holds either a registry coordinate
 * (when {@link #getSourceType()} is {@code REGISTRY}) or a storage key for an
 * embedded skill archive ({@code EMBEDDED}). Display fields are snapshots — the
 * bundle keeps showing the original skill name even if upstream renames.
 */
@Entity
@Table(name = "skill_bundle_item")
public class SkillBundleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_version_id", nullable = false)
    private Long bundleVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private BundleItemSourceType sourceType;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @Column(name = "embedded_skill_key", length = 512)
    private String embeddedSkillKey;

    @Column(name = "namespace_slug", nullable = false, length = 128)
    private String namespaceSlug;

    @Column(name = "skill_slug", nullable = false, length = 128)
    private String skillSlug;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(length = 512)
    private String summary;

    @Column(name = "role_description", nullable = false, length = 512)
    private String roleDescription;

    @Column(nullable = false)
    private boolean required = true;

    @Column(name = "install_order", nullable = false)
    private int installOrder;

    @Column(name = "compatibility_json", columnDefinition = "jsonb")
    private String compatibilityJson;

    protected SkillBundleItem() {}

    public SkillBundleItem(Long bundleVersionId, BundleItemSourceType sourceType,
                           String namespaceSlug, String skillSlug, String version,
                           String displayName, String roleDescription,
                           boolean required, int installOrder) {
        this.bundleVersionId = bundleVersionId;
        this.sourceType = sourceType;
        this.namespaceSlug = namespaceSlug;
        this.skillSlug = skillSlug;
        this.version = version;
        this.displayName = displayName;
        this.roleDescription = roleDescription;
        this.required = required;
        this.installOrder = installOrder;
    }

    public Long getId() { return id; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public BundleItemSourceType getSourceType() { return sourceType; }
    public Long getSkillId() { return skillId; }
    public Long getSkillVersionId() { return skillVersionId; }
    public String getEmbeddedSkillKey() { return embeddedSkillKey; }
    public String getNamespaceSlug() { return namespaceSlug; }
    public String getSkillSlug() { return skillSlug; }
    public String getVersion() { return version; }
    public String getDisplayName() { return displayName; }
    public String getSummary() { return summary; }
    public String getRoleDescription() { return roleDescription; }
    public boolean isRequired() { return required; }
    public int getInstallOrder() { return installOrder; }
    public String getCompatibilityJson() { return compatibilityJson; }

    public void setSkillId(Long skillId) { this.skillId = skillId; }
    public void setSkillVersionId(Long skillVersionId) { this.skillVersionId = skillVersionId; }
    public void setEmbeddedSkillKey(String embeddedSkillKey) { this.embeddedSkillKey = embeddedSkillKey; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setCompatibilityJson(String compatibilityJson) { this.compatibilityJson = compatibilityJson; }
}
