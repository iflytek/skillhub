package com.iflytek.skillhub.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.db.MysqlContainerBackedDataJpaTest;
import com.iflytek.skillhub.db.MysqlMigrationDataJpaTest;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.AuditLogJpaRepository;
import com.iflytek.skillhub.infra.jpa.ProfileChangeRequestJpaRepository;
import com.iflytek.skillhub.infra.jpa.SecurityAuditJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

@MysqlMigrationDataJpaTest
@Testcontainers
class AuditAndInfraJsonPersistenceTest extends MysqlContainerBackedDataJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SecurityAuditJpaRepository securityAuditRepository;

    @Autowired
    private ProfileChangeRequestJpaRepository profileChangeRequestRepository;

    @Autowired
    private AuditLogJpaRepository auditLogRepository;

    @Test
    void persistsSecurityAuditFindingsAsPlainText() {
        SkillVersion version = persistSkillVersionGraph("security-owner", "security-team", "security-skill");

        SecurityAudit audit = new SecurityAudit(version.getId(), ScannerType.SKILL_SCANNER);
        audit.setFindings("[{\"severity\":\"HIGH\",\"rule\":\"exec\"}]");
        audit.setFindingsCount(1);

        SecurityAudit saved = securityAuditRepository.saveAndFlush(audit);
        entityManager.clear();

        SecurityAudit reloaded = securityAuditRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFindings()).isEqualTo("[{\"severity\":\"HIGH\",\"rule\":\"exec\"}]");
    }

    @Test
    void persistsProfileChangeRequestJsonFieldsAsPlainText() {
        entityManager.persist(new UserAccount("profile-owner", "Profile Owner", "profile@example.com", null));

        ProfileChangeRequest request = new ProfileChangeRequest(
                "profile-owner",
                "{\"displayName\":\"New Name\"}",
                "{\"displayName\":\"Old Name\"}",
                ProfileChangeStatus.PENDING,
                "PASS",
                null
        );

        ProfileChangeRequest saved = profileChangeRequestRepository.saveAndFlush(request);
        entityManager.clear();

        ProfileChangeRequest reloaded = entityManager.find(ProfileChangeRequest.class, saved.getId());
        assertThat(reloaded.getChanges()).isEqualTo("{\"displayName\":\"New Name\"}");
        assertThat(reloaded.getOldValues()).isEqualTo("{\"displayName\":\"Old Name\"}");
    }

    @Test
    void persistsAuditLogDetailJsonAsPlainText() {
        AuditLog log = new AuditLog(
                "auditor-1",
                "PROFILE_REVIEWED",
                "PROFILE_CHANGE_REQUEST",
                42L,
                "req-1",
                "127.0.0.1",
                "JUnit",
                "{\"decision\":\"APPROVED\",\"reviewer\":\"auditor-1\"}",
                Instant.parse("2026-05-03T02:30:00Z")
        );

        AuditLog saved = auditLogRepository.saveAndFlush(log);
        entityManager.clear();

        AuditLog reloaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDetailJson()).isEqualTo("{\"decision\":\"APPROVED\",\"reviewer\":\"auditor-1\"}");
    }

    private SkillVersion persistSkillVersionGraph(String userId, String namespaceSlug, String skillSlug) {
        UserAccount owner = new UserAccount(userId, "Owner " + userId, userId + "@example.com", null);
        entityManager.persist(owner);

        Namespace namespace = new Namespace(namespaceSlug, "Namespace " + namespaceSlug, owner.getId());
        entityManager.persist(namespace);

        Skill skill = new Skill(namespace.getId(), skillSlug, owner.getId(), SkillVisibility.PUBLIC);
        entityManager.persist(skill);

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", owner.getId());
        entityManager.persist(version);
        entityManager.flush();
        return version;
    }
}
