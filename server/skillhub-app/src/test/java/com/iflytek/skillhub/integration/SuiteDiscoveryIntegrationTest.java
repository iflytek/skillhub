package com.iflytek.skillhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteMemberSelection;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMember;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMemberRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionStatus;
import com.iflytek.skillhub.domain.suite.SkillSuiteStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.search.postgres.PostgresResourceDiscoveryQueryService;
import com.iflytek.skillhub.service.ResourceDiscoveryAppService;
import com.iflytek.skillhub.repository.MySkillSuiteQueryRepository;
import com.iflytek.skillhub.repository.SkillSuiteReferenceQueryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PostgresResourceDiscoveryQueryService.class, ResourceDiscoveryAppService.class,
        MySkillSuiteQueryRepository.class, SkillSuiteReferenceQueryRepository.class})
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class SuiteDiscoveryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ResourceDiscoveryAppService appService;

    @Autowired
    private MySkillSuiteQueryRepository mySuiteRepository;

    @Autowired
    private SkillSuiteReferenceQueryRepository suiteReferenceRepository;

    @Autowired
    private SkillSuiteVersionMemberRepository suiteMemberRepository;

    @BeforeEach
    void seedReferencedUsers() {
        entityManager.persist(new UserAccount("owner", "Owner", null, null));
        entityManager.persist(new UserAccount("author", "Author", null, null));
        entityManager.persist(new UserAccount("other-author", "Other Author", null, null));
        entityManager.flush();
    }

    @Test
    void returnsSkillAndSuiteWithTheSameCoordinateAsDistinctResourceTypes() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("team-ai", "AI Team", "owner"));
        Skill skill = new Skill(namespace.getId(), "starter", "owner", SkillVisibility.PUBLIC);
        skill.setDisplayName("Starter Skill");
        skill.setSummary("A standalone skill");
        skill = entityManager.persistFlushFind(skill);

        SkillVersion skillVersion = new SkillVersion(skill.getId(), "1.2.0", "owner");
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        skillVersion.setDownloadReady(true);
        skillVersion.setPublishedAt(Instant.parse("2026-09-01T10:00:00Z"));
        skillVersion = entityManager.persistFlushFind(skillVersion);
        skill.setLatestVersionId(skillVersion.getId());
        entityManager.persistAndFlush(skill);

        SkillSuite suite = new SkillSuite(namespace.getId(), "starter", "Mutable container name", "owner");
        suite.setSummary("Mutable container summary");
        suite = entityManager.persistFlushFind(suite);
        SkillSuiteVersion suiteVersion = new SkillSuiteVersion(
                suite.getId(), "2.0.0", "Published snapshot name", "Published snapshot summary",
                SkillVisibility.PUBLIC, "owner");
        suiteVersion.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suiteVersion.setPublishedAt(Instant.parse("2026-09-02T10:00:00Z"));
        suiteVersion = entityManager.persistFlushFind(suiteVersion);
        entityManager.persist(new SkillSuiteVersionMember(
                suiteVersion.getId(),
                new SkillSuiteMemberSelection(
                        skill.getId(), skillVersion.getId(), namespace.getSlug(),
                        skill.getSlug(), skillVersion.getVersion(), "a".repeat(64)),
                0,
                true));
        suite.setLatestVersionId(suiteVersion.getId());
        entityManager.persistAndFlush(suite);
        entityManager.clear();

        var result = appService.search("starter", "team-ai", "", "relevance", 0, 20, Set.of());

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting(item -> item.resourceType())
                .containsExactlyInAnyOrder("SKILL", "SUITE");
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.namespace()).isEqualTo("team-ai");
            assertThat(item.slug()).isEqualTo("starter");
            assertThat(item.available()).isTrue();
        });
        assertThat(result.items()).extracting(item -> item.detailUrl())
                .containsExactlyInAnyOrder("/space/team-ai/starter", "/suite/team-ai/starter");
        assertThat(result.items()).filteredOn(item -> "SUITE".equals(item.resourceType()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.displayName()).isEqualTo("Published snapshot name");
                    assertThat(item.summary()).isEqualTo("Published snapshot summary");
                });
        assertThat(suiteReferenceRepository.findVisibleEntryReferences(
                skill.getId(), null, Map.of(), Set.of()))
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.namespace()).isEqualTo("team-ai");
                    assertThat(reference.slug()).isEqualTo("starter");
                    assertThat(reference.version()).isEqualTo("2.0.0");
                    assertThat(reference.memberCount()).isEqualTo(1);
                });
    }

    @Test
    void doesNotLeakPrivateSuiteEntryReferenceToUnrelatedViewers() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("private-suite-team", "Private Suite Team", "owner"));
        Skill skill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "entry", "owner", SkillVisibility.PUBLIC));
        SkillVersion skillVersion = new SkillVersion(skill.getId(), "1.0.0", "owner");
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        skillVersion.setDownloadReady(true);
        skillVersion = entityManager.persistFlushFind(skillVersion);
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "private-suite", "Private Suite", "author"));
        SkillSuiteVersion suiteVersion = new SkillSuiteVersion(
                suite.getId(), "1.0.0", SkillVisibility.PRIVATE, "author");
        suiteVersion.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suiteVersion = entityManager.persistFlushFind(suiteVersion);
        entityManager.persist(new SkillSuiteVersionMember(
                suiteVersion.getId(),
                new SkillSuiteMemberSelection(
                        skill.getId(), skillVersion.getId(), namespace.getSlug(),
                        skill.getSlug(), skillVersion.getVersion(), "b".repeat(64)),
                0,
                true));
        suite.setLatestVersionId(suiteVersion.getId());
        entityManager.persistAndFlush(suite);
        entityManager.clear();

        assertThat(suiteReferenceRepository.findVisibleEntryReferences(
                skill.getId(), null, Map.of(), Set.of())).isEmpty();
        assertThat(suiteReferenceRepository.findVisibleEntryReferences(
                skill.getId(), "other-author", Map.of(namespace.getId(), NamespaceRole.MEMBER), Set.of()))
                .isEmpty();
        assertThat(suiteReferenceRepository.findVisibleEntryReferences(
                skill.getId(), "author", Map.of(namespace.getId(), NamespaceRole.MEMBER), Set.of()))
                .singleElement()
                .satisfies(reference -> assertThat(reference.slug()).isEqualTo("private-suite"));

        suite = entityManager.find(SkillSuite.class, suite.getId());
        suite.setHidden(true);
        entityManager.persistAndFlush(suite);
        entityManager.clear();
        assertThat(suiteReferenceRepository.findVisibleMemberships(
                skill.getId(), "author", Map.of(namespace.getId(), NamespaceRole.MEMBER),
                Set.of(), 0, 20).items()).isEmpty();

        suite = entityManager.find(SkillSuite.class, suite.getId());
        suite.setHidden(false);
        suite.setStatus(SkillSuiteStatus.ARCHIVED);
        entityManager.persistAndFlush(suite);
        entityManager.clear();
        assertThat(suiteReferenceRepository.findVisibleMemberships(
                skill.getId(), "author", Map.of(namespace.getId(), NamespaceRole.MEMBER),
                Set.of(), 0, 20).items()).isEmpty();
    }

    @Test
    void findsOrdinaryMembershipAndProtectsPrivateSiblingMetadata() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("member-team", "Member Team", "owner"));
        PublishedSkill current = publishedSkill(
                namespace, "ordinary", "owner", SkillVisibility.PUBLIC, "Ordinary");
        PublishedSkill entry = publishedSkill(
                namespace, "entry", "owner", SkillVisibility.PUBLIC, "Entry");
        PublishedSkill restricted = publishedSkill(
                namespace, "private-helper", "other-author", SkillVisibility.PRIVATE,
                "Private Helper");

        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "member-pack", "Member Pack", "author"));
        SkillSuiteVersion suiteVersion = new SkillSuiteVersion(
                suite.getId(), "2.0.0", "Member Pack", "Current members",
                SkillVisibility.PUBLIC, "author");
        suiteVersion.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suiteVersion = entityManager.persistFlushFind(suiteVersion);
        persistMember(suiteVersion, entry, 0, true);
        persistMember(suiteVersion, current, 1, false);
        persistMember(suiteVersion, restricted, 2, false);
        for (int index = 0; index < 9; index++) {
            persistMember(suiteVersion, publishedSkill(
                    namespace, "helper-" + index, "owner", SkillVisibility.PUBLIC,
                    "Helper " + index), index + 3, false);
        }
        suite.setLatestVersionId(suiteVersion.getId());
        entityManager.persistAndFlush(suite);
        entityManager.clear();

        var page = suiteReferenceRepository.findVisibleMemberships(
                current.skill().getId(), null, Map.of(), Set.of(), 0, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(reference -> {
            assertThat(reference.slug()).isEqualTo("member-pack");
            assertThat(reference.currentSkillEntry()).isFalse();
            assertThat(reference.memberCount()).isEqualTo(12);
            assertThat(reference.visibleSiblingMembers()).hasSize(8);
            assertThat(reference.visibleSiblingMembers().getFirst()).satisfies(member -> {
                assertThat(member.slug()).isEqualTo("entry");
                assertThat(member.entry()).isTrue();
                assertThat(member.available()).isTrue();
            });
            assertThat(reference.restrictedMemberCount()).isEqualTo(1);
            assertThat(reference.omittedVisibleMemberCount()).isEqualTo(2);
        });

        entityManager.getEntityManager().createNativeQuery(
                        "UPDATE skill SET latest_version_id = NULL WHERE id = :id")
                .setParameter("id", restricted.skill().getId())
                .executeUpdate();
        entityManager.getEntityManager().createNativeQuery("DELETE FROM skill_version WHERE id = :id")
                .setParameter("id", restricted.version().getId())
                .executeUpdate();
        entityManager.getEntityManager().createNativeQuery("DELETE FROM skill WHERE id = :id")
                .setParameter("id", restricted.skill().getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(suiteReferenceRepository.findVisibleMemberships(
                current.skill().getId(), null, Map.of(), Set.of(), 0, 20).items())
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.restrictedMemberCount()).isEqualTo(1);
                    assertThat(reference.visibleSiblingMembers())
                            .noneMatch(member -> member.slug().equals("private-helper"));
                });
    }

    @Test
    void ignoresHistoricalMembershipOutsideLatestSuiteSnapshot() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("history-team", "History Team", "owner"));
        PublishedSkill removed = publishedSkill(
                namespace, "removed", "owner", SkillVisibility.PUBLIC, "Removed");
        PublishedSkill replacement = publishedSkill(
                namespace, "replacement", "owner", SkillVisibility.PUBLIC, "Replacement");
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "evolving-pack", "Evolving Pack", "owner"));

        SkillSuiteVersion oldVersion = new SkillSuiteVersion(
                suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "owner");
        oldVersion.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        oldVersion = entityManager.persistFlushFind(oldVersion);
        persistMember(oldVersion, removed, 0, true);

        SkillSuiteVersion latestVersion = new SkillSuiteVersion(
                suite.getId(), "2.0.0", SkillVisibility.PUBLIC, "owner");
        latestVersion.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        latestVersion = entityManager.persistFlushFind(latestVersion);
        persistMember(latestVersion, replacement, 0, true);
        suite.setLatestVersionId(latestVersion.getId());
        entityManager.persistAndFlush(suite);
        entityManager.clear();

        assertThat(suiteReferenceRepository.findVisibleMemberships(
                removed.skill().getId(), null, Map.of(), Set.of(), 0, 20).items()).isEmpty();
    }

    @Test
    void boundsMembershipPagesAndExposesTotalForContinuation() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("many-suite-team", "Many Suite Team", "owner"));
        PublishedSkill current = publishedSkill(
                namespace, "popular-member", "owner", SkillVisibility.PUBLIC, "Popular Member");
        for (int index = 0; index < 21; index++) {
            SkillSuite suite = new SkillSuite(
                    namespace.getId(), "pack-" + index, "Pack " + index, "owner");
            entityManager.persist(suite);
            SkillSuiteVersion version = new SkillSuiteVersion(
                    suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "owner");
            version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
            entityManager.persist(version);
            persistMember(version, current, 0, true);
            suite.setLatestVersionId(version.getId());
        }
        entityManager.flush();
        entityManager.clear();

        var first = suiteReferenceRepository.findVisibleMemberships(
                current.skill().getId(), null, Map.of(), Set.of(), 0, 100);
        var second = suiteReferenceRepository.findVisibleMemberships(
                current.skill().getId(), null, Map.of(), Set.of(), 1, 100);

        assertThat(first.size()).isEqualTo(20);
        assertThat(first.total()).isEqualTo(21);
        assertThat(first.items()).hasSize(20);
        assertThat(second.total()).isEqualTo(21);
        assertThat(second.items()).hasSize(1);
    }

    @Test
    void exposesNamespaceOnlyResourcesOnlyToNamespaceMembers() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("private-team", "Private Team", "owner"));
        Skill skill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "internal", "owner", SkillVisibility.NAMESPACE_ONLY));
        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", "owner");
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        version = entityManager.persistFlushFind(version);
        skill.setLatestVersionId(version.getId());
        entityManager.persistAndFlush(skill);
        entityManager.clear();

        assertThat(appService.search("", "", "SKILL", "newest", 0, 20, Set.of()).items())
                .isEmpty();
        assertThat(appService.search(
                "", "", "SKILL", "newest", 0, 20, Set.of(namespace.getId())).items())
                .singleElement()
                .satisfies(item -> assertThat(item.slug()).isEqualTo("internal"));
    }

    @Test
    void exposesNamespaceOnlySuiteMembershipOnlyToNamespaceMembers() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("membership-team", "Membership Team", "owner"));
        PublishedSkill current = publishedSkill(
                namespace, "shared-member", "owner", SkillVisibility.PUBLIC, "Shared Member");
        SkillSuite suite = entityManager.persistFlushFind(new SkillSuite(
                namespace.getId(), "internal-pack", "Internal Pack", "owner"));
        SkillSuiteVersion version = new SkillSuiteVersion(
                suite.getId(), "1.0.0", SkillVisibility.NAMESPACE_ONLY, "owner");
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        version = entityManager.persistFlushFind(version);
        persistMember(version, current, 0, true);
        suite.setLatestVersionId(version.getId());
        entityManager.persistAndFlush(suite);
        entityManager.clear();

        assertThat(suiteReferenceRepository.findVisibleMemberships(
                current.skill().getId(), null, Map.of(), Set.of(), 0, 20).items()).isEmpty();
        assertThat(suiteReferenceRepository.findVisibleMemberships(
                current.skill().getId(), "author",
                Map.of(namespace.getId(), NamespaceRole.MEMBER), Set.of(), 0, 20).items())
                .singleElement()
                .satisfies(reference -> assertThat(reference.slug()).isEqualTo("internal-pack"));
    }

    @Test
    void dashboardReturnsTheLatestVersionTheCallerCanManage() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("managed-team", "Managed Team", "owner"));
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "writers", "Writers", "author"));
        SkillSuiteVersion ownVersion = entityManager.persistFlushFind(
                new SkillSuiteVersion(suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "author"));
        SkillSuiteVersion adminVersion = entityManager.persistFlushFind(
                new SkillSuiteVersion(
                        suite.getId(), "2.0.0", "Writers 2", "Second draft",
                        SkillVisibility.PUBLIC, "other-author"));
        entityManager.clear();

        var authorPage = mySuiteRepository.findMine(
                "author", Set.of(namespace.getId()), Set.of(), "writers", 0, 20);
        assertThat(authorPage.items()).singleElement().satisfies(item -> {
            assertThat(item.versionId()).isEqualTo(adminVersion.getId());
            assertThat(item.version()).isEqualTo("2.0.0");
        });
        assertThat(mySuiteRepository.findMine(
                "other-author", Set.of(namespace.getId()), Set.of(), "writers", 0, 20).items())
                .isEmpty();

        var adminPage = mySuiteRepository.findMine(
                "owner", Set.of(namespace.getId()), Set.of(namespace.getId()), "", 0, 20);
        assertThat(adminPage.items()).singleElement().satisfies(item -> {
            assertThat(item.versionId()).isEqualTo(adminVersion.getId());
            assertThat(item.version()).isEqualTo("2.0.0");
            assertThat(item.displayName()).isEqualTo("Writers 2");
            assertThat(item.summary()).isEqualTo("Second draft");
        });
    }

    @Test
    void hardDeletingMemberSkillPreservesThePublishedSuiteSnapshot() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("snapshot-team", "Snapshot Team", "owner"));
        Skill skill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "archived-writer", "owner", SkillVisibility.PUBLIC));
        SkillVersion skillVersion = new SkillVersion(skill.getId(), "3.1.4", "owner");
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        skillVersion = entityManager.persistFlushFind(skillVersion);
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "historical-pack", "Historical Pack", "owner"));
        SkillSuiteVersion suiteVersion = new SkillSuiteVersion(
                suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "owner");
        suiteVersion.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        suiteVersion = entityManager.persistFlushFind(suiteVersion);
        SkillSuiteVersionMember member = entityManager.persistFlushFind(
                new SkillSuiteVersionMember(
                        suiteVersion.getId(),
                        new SkillSuiteMemberSelection(
                                skill.getId(), skillVersion.getId(), "snapshot-team",
                                "archived-writer", "3.1.4", "sha512:" + "b".repeat(128)),
                        0,
                        true));

        entityManager.getEntityManager().createNativeQuery("DELETE FROM skill_version WHERE id = :id")
                .setParameter("id", skillVersion.getId())
                .executeUpdate();
        entityManager.getEntityManager().createNativeQuery("DELETE FROM skill WHERE id = :id")
                .setParameter("id", skill.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Object[] snapshot = (Object[]) entityManager.getEntityManager().createNativeQuery("""
                SELECT skill_id, skill_version_id, namespace_slug_snapshot,
                       skill_slug_snapshot, skill_version_snapshot, fingerprint_snapshot, entry
                FROM skill_suite_version_member
                WHERE id = :id
                """).setParameter("id", member.getId()).getSingleResult();
        assertThat(snapshot[0]).isNull();
        assertThat(snapshot[1]).isNull();
        assertThat(snapshot[2]).isEqualTo("snapshot-team");
        assertThat(snapshot[3]).isEqualTo("archived-writer");
        assertThat(snapshot[4]).isEqualTo("3.1.4");
        assertThat(snapshot[5]).isEqualTo("sha512:" + "b".repeat(128));
        assertThat(snapshot[6]).isEqualTo(true);
    }

    @Test
    void replacesSuiteMembersAtTheSamePositionWithoutAUniqueConstraintConflict() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("replace-team", "Replace Team", "owner"));
        Skill skill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "replacement", "owner", SkillVisibility.PUBLIC));
        SkillVersion firstVersion = entityManager.persistFlushFind(
                new SkillVersion(skill.getId(), "1.0.0", "owner"));
        SkillVersion secondVersion = entityManager.persistFlushFind(
                new SkillVersion(skill.getId(), "2.0.0", "owner"));
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "replaceable", "Replaceable", "owner"));
        SkillSuiteVersion suiteVersion = entityManager.persistFlushFind(
                new SkillSuiteVersion(suite.getId(), "1.0.0", SkillVisibility.PRIVATE, "owner"));
        entityManager.persistAndFlush(new SkillSuiteVersionMember(
                suiteVersion.getId(),
                new SkillSuiteMemberSelection(
                        skill.getId(), firstVersion.getId(), "replace-team",
                        "replacement", "1.0.0", "sha256:" + "a".repeat(64)),
                0,
                true));
        entityManager.clear();

        suiteMemberRepository.deleteBySuiteVersionId(suiteVersion.getId());
        suiteMemberRepository.saveAll(List.of(new SkillSuiteVersionMember(
                suiteVersion.getId(),
                new SkillSuiteMemberSelection(
                        skill.getId(), secondVersion.getId(), "replace-team",
                        "replacement", "2.0.0", "sha256:" + "b".repeat(64)),
                0,
                true)));
        entityManager.clear();

        assertThat(suiteMemberRepository.findBySuiteVersionIdOrderByPosition(suiteVersion.getId()))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getPosition()).isZero();
                    assertThat(member.getSkillVersionId()).isEqualTo(secondVersion.getId());
                    assertThat(member.getSkillVersionSnapshot()).isEqualTo("2.0.0");
                });
    }

    private PublishedSkill publishedSkill(
            Namespace namespace,
            String slug,
            String ownerId,
            SkillVisibility visibility,
            String displayName
    ) {
        Skill skill = new Skill(namespace.getId(), slug, ownerId, visibility);
        skill.setDisplayName(displayName);
        entityManager.persist(skill);
        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", ownerId);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        entityManager.persist(version);
        skill.setLatestVersionId(version.getId());
        return new PublishedSkill(skill, version, namespace.getSlug());
    }

    private void persistMember(
            SkillSuiteVersion suiteVersion,
            PublishedSkill publishedSkill,
            int position,
            boolean entry
    ) {
        entityManager.persist(new SkillSuiteVersionMember(
                suiteVersion.getId(),
                new SkillSuiteMemberSelection(
                        publishedSkill.skill().getId(), publishedSkill.version().getId(),
                        publishedSkill.namespaceSlug(),
                        publishedSkill.skill().getSlug(), publishedSkill.version().getVersion(),
                        "sha256:" + Integer.toHexString(position).repeat(64).substring(0, 64)),
                position,
                entry));
    }

    private record PublishedSkill(Skill skill, SkillVersion version, String namespaceSlug) {
    }
}
