package com.iflytek.skillhub.domain.suite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Exact Skill version reference captured by a Suite version.
 * Snapshot fields remain populated when governance hard-deletes the referenced Skill or version.
 */
@Entity
@Table(name = "skill_suite_version_member")
public class SkillSuiteVersionMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suite_version_id", nullable = false)
    private Long suiteVersionId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private boolean entry;

    @Column(name = "namespace_slug_snapshot", nullable = false, length = 128)
    private String namespaceSlugSnapshot;

    @Column(name = "skill_slug_snapshot", nullable = false, length = 128)
    private String skillSlugSnapshot;

    @Column(name = "skill_version_snapshot", nullable = false, length = 64)
    private String skillVersionSnapshot;

    // Fingerprints include the algorithm prefix (for example, "sha256:") and the digest.
    @Column(name = "fingerprint_snapshot", nullable = false, length = 255)
    private String fingerprintSnapshot;

    protected SkillSuiteVersionMember() {
    }

    public SkillSuiteVersionMember(
            Long suiteVersionId,
            SkillSuiteMemberSelection selection,
            int position,
            boolean entry
    ) {
        this.suiteVersionId = suiteVersionId;
        this.skillId = selection.skillId();
        this.skillVersionId = selection.skillVersionId();
        this.position = position;
        this.entry = entry;
        this.namespaceSlugSnapshot = selection.namespaceSlug();
        this.skillSlugSnapshot = selection.skillSlug();
        this.skillVersionSnapshot = selection.version();
        this.fingerprintSnapshot = selection.fingerprint();
    }

    public Long getId() { return id; }
    public Long getSuiteVersionId() { return suiteVersionId; }
    public Long getSkillId() { return skillId; }
    public Long getSkillVersionId() { return skillVersionId; }
    public Integer getPosition() { return position; }
    public boolean isEntry() { return entry; }
    public String getNamespaceSlugSnapshot() { return namespaceSlugSnapshot; }
    public String getSkillSlugSnapshot() { return skillSlugSnapshot; }
    public String getSkillVersionSnapshot() { return skillVersionSnapshot; }
    public String getFingerprintSnapshot() { return fingerprintSnapshot; }
}
