package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.bootstrap.BuiltinSkillManifestLoader.ManifestItem;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = SkillhubApplication.class,
        properties = {
                "skillhub.builtin-skills.enabled=false",
                "skillhub.bootstrap.admin.enabled=false",
                "logging.level.com.iflytek.skillhub.bootstrap.BuiltinSkillInitializer=INFO"
        })
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class BuiltinSkillUpgradeConflictIntegrationTest {

    private static final String GLOBAL = "global";
    private static final String SYSTEM_PUBLISHER = "builtin-skill-publisher";
    private static final String USER_PUBLISHER = "existing-weather-owner";
    private static final Set<String> SUPER_ADMIN = Set.of("SUPER_ADMIN");
    private static final Set<String> PREEXISTING_SLUGS = Set.of("skillhub-hello", "agentguard", "weather");

    private static final List<SkillCoordinate> RELEASE_SKILLS = List.of(
            new SkillCoordinate("skillhub-hello", "1.0.0"),
            new SkillCoordinate("agentguard", "1.1"),
            new SkillCoordinate("ai-claim-checker", "1.0.0"),
            new SkillCoordinate("daily-standup-journal", "1.0.0"),
            new SkillCoordinate("decision-matrix", "1.0.0"),
            new SkillCoordinate("diagram-maker", "1.0.0"),
            new SkillCoordinate("documentation-writer", "1.0.0"),
            new SkillCoordinate("exam-ready", "1.0.0"),
            new SkillCoordinate("frontend-design", "1.0.0"),
            new SkillCoordinate("linkedin-post-formatter", "1.0.0"),
            new SkillCoordinate("meeting-note-summarizer", "1.0.0"),
            new SkillCoordinate("retrieval-practice-generator", "1.0.0"),
            new SkillCoordinate("storytelling-advisor", "1.0.0"),
            new SkillCoordinate("study-strategy-selector", "1.0.0"),
            new SkillCoordinate("time-blocking-scheduler", "1.0.0"),
            new SkillCoordinate("video-frames", "1.0.0"),
            new SkillCoordinate("weather", "1.0.0")
    );

    @Autowired
    private BuiltinSkillInitializer initializer;

    @Autowired
    private BuiltinSkillProperties properties;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private NamespaceMemberRepository namespaceMemberRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillVersionRepository skillVersionRepository;

    @Autowired
    private SkillFileRepository skillFileRepository;

    @Autowired
    private SkillPublishService skillPublishService;

    @MockBean
    private BuiltinSkillManifestLoader manifestLoader;

    @MockBean
    private BuiltinSkillRemotePackageDownloader downloader;

    @MockBean
    private BuiltinSkillPackageExtractor extractor;

    @MockBean
    private ObjectStorageService objectStorageService;

    @MockBean
    private SecurityScanService securityScanService;

    @MockBean
    private SearchRebuildService searchRebuildService;

    @BeforeEach
    void setUp() {
        when(securityScanService.isEnabled()).thenReturn(true);
    }

    @Test
    void upgradePreservesUserOwnedConflictAndPublishesRemainingBuiltIns(CapturedOutput output) throws Exception {
        Namespace global = new Namespace(GLOBAL, "Global", "system");
        global.setType(NamespaceType.GLOBAL);
        global = namespaceRepository.save(global);

        userAccountRepository.save(UserAccount.systemAccount(
                SYSTEM_PUBLISHER,
                "Built-in Skill Publisher",
                null,
                null
        ));
        userAccountRepository.save(new UserAccount(
                USER_PUBLISHER,
                "Existing Weather Owner",
                "weather-owner@example.test",
                null
        ));
        namespaceMemberRepository.save(new NamespaceMember(global.getId(), SYSTEM_PUBLISHER, NamespaceRole.OWNER));
        namespaceMemberRepository.save(new NamespaceMember(global.getId(), USER_PUBLISHER, NamespaceRole.OWNER));

        SkillPublishService.PublishResult hello = publishExisting(
                "skillhub-hello", "1.0.0", SYSTEM_PUBLISHER, "existing system hello content");
        SkillPublishService.PublishResult agentguard = publishExisting(
                "agentguard", "1.1", SYSTEM_PUBLISHER, "existing system agentguard content");
        SkillPublishService.PublishResult weather = publishExisting(
                "weather", "1.0.0", USER_PUBLISHER, "existing user weather content");

        SkillSnapshot helloBefore = snapshot(hello.skillId(), "1.0.0");
        SkillSnapshot agentguardBefore = snapshot(agentguard.skillId(), "1.1");
        SkillSnapshot weatherBefore = snapshot(weather.skillId(), "1.0.0");

        List<ManifestItem> manifest = RELEASE_SKILLS.stream()
                .map(BuiltinSkillUpgradeConflictIntegrationTest::manifestItem)
                .toList();
        when(manifestLoader.load()).thenReturn(manifest);
        for (ManifestItem item : manifest) {
            byte[] archive = archiveBytes(item);
            when(downloader.download(URI.create(item.url()))).thenReturn(Optional.of(archive));
            when(extractor.extract(archive)).thenReturn(new SkillPackageArchiveExtractor.ExtractionResult(
                    packageEntries(item.slug(), item.version(), "official " + item.slug() + " content"),
                    List.of()
            ));
        }

        properties.setEnabled(true);
        initializer.synchronize();

        assertThat(snapshot(hello.skillId(), "1.0.0")).isEqualTo(helloBefore);
        assertThat(snapshot(agentguard.skillId(), "1.1")).isEqualTo(agentguardBefore);
        assertThat(snapshot(weather.skillId(), "1.0.0")).isEqualTo(weatherBefore);

        List<Skill> weatherSkills = skillRepository.findByNamespaceIdAndSlug(global.getId(), "weather");
        assertThat(weatherSkills).singleElement().satisfies(existing -> {
            assertThat(existing.getId()).isEqualTo(weather.skillId());
            assertThat(existing.getOwnerId()).isEqualTo(USER_PUBLISHER);
        });
        assertThat(weatherSkills).noneMatch(skill -> SYSTEM_PUBLISHER.equals(skill.getOwnerId()));

        List<SkillCoordinate> newlyPublished = RELEASE_SKILLS.stream()
                .filter(item -> !PREEXISTING_SLUGS.contains(item.slug()))
                .toList();
        assertThat(newlyPublished).hasSize(14);
        for (SkillCoordinate item : newlyPublished) {
            List<Skill> skills = skillRepository.findByNamespaceIdAndSlug(global.getId(), item.slug());
            assertThat(skills).singleElement().satisfies(skill -> {
                assertThat(skill.getOwnerId()).isEqualTo(SYSTEM_PUBLISHER);
                SkillVersion version = skillVersionRepository
                        .findBySkillIdAndVersion(skill.getId(), item.version())
                        .orElseThrow();
                assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
                assertThat(skill.getLatestVersionId()).isEqualTo(version.getId());
                assertThat(skillFileRepository.findByVersionId(version.getId())).hasSize(2);
            });
        }

        assertThat(skillRepository.findAll()).hasSize(17);
        assertThat(skillRepository.findByOwnerId(SYSTEM_PUBLISHER)).hasSize(16);
        assertThat(skillVersionRepository.findBySkillId(hello.skillId())).hasSize(1);
        assertThat(skillVersionRepository.findBySkillId(agentguard.skillId())).hasSize(1);

        for (String skippedSlug : PREEXISTING_SLUGS) {
            ManifestItem skipped = manifest.stream()
                    .filter(item -> item.slug().equals(skippedSlug))
                    .findFirst()
                    .orElseThrow();
            verify(downloader, never()).download(URI.create(skipped.url()));
        }
        verify(downloader, times(14)).download(any(URI.class));

        assertThat(output).contains(
                "Built-in skill synchronization finished: total=17, published=14, "
                        + "idempotentSkipped=2, conflictSkipped=1, failed=0"
        );
    }

    private SkillPublishService.PublishResult publishExisting(
            String slug,
            String version,
            String publisherId,
            String content) {
        return skillPublishService.publishFromEntries(
                GLOBAL,
                packageEntries(slug, version, content),
                publisherId,
                SkillVisibility.PUBLIC,
                SUPER_ADMIN,
                true
        );
    }

    private SkillSnapshot snapshot(Long skillId, String versionName) {
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        SkillVersion version = skillVersionRepository
                .findBySkillIdAndVersion(skillId, versionName)
                .orElseThrow();
        List<FileSnapshot> files = skillFileRepository.findByVersionId(version.getId()).stream()
                .sorted(Comparator.comparing(SkillFile::getFilePath))
                .map(FileSnapshot::from)
                .toList();
        return new SkillSnapshot(
                skill.getId(),
                skill.getOwnerId(),
                skill.getLatestVersionId(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getUpdatedAt(),
                version.getId(),
                version.getVersion(),
                version.getStatus(),
                version.getParsedMetadataJson(),
                version.getManifestJson(),
                version.getCreatedAt(),
                files
        );
    }

    private static ManifestItem manifestItem(SkillCoordinate coordinate) {
        String url = "https://bjcdn.openstorage.cn/integration/builtin-skills/"
                + coordinate.slug() + "/" + coordinate.version() + ".zip";
        ManifestItem item = new ManifestItem(coordinate.slug(), coordinate.version(), url, "");
        return new ManifestItem(item.slug(), item.version(), item.url(), sha256(archiveBytes(item)));
    }

    private static byte[] archiveBytes(ManifestItem item) {
        return ("archive:" + item.slug() + ":" + item.version()).getBytes(StandardCharsets.UTF_8);
    }

    private static List<PackageEntry> packageEntries(String name, String version, String readme) {
        byte[] skillMd = ("""
                ---
                name: %s
                description: Integration fixture for %s
                version: %s
                ---
                # %s
                """).formatted(name, name, version, name).getBytes(StandardCharsets.UTF_8);
        byte[] readmeBytes = readme.getBytes(StandardCharsets.UTF_8);
        return List.of(
                new PackageEntry("SKILL.md", skillMd, skillMd.length, "text/markdown"),
                new PackageEntry("README.md", readmeBytes, readmeBytes.length, "text/markdown")
        );
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SkillCoordinate(String slug, String version) {
    }

    private record FileSnapshot(
            Long id,
            String path,
            Long size,
            String contentType,
            String sha256,
            String storageKey,
            Instant createdAt) {

        private static FileSnapshot from(SkillFile file) {
            return new FileSnapshot(
                    file.getId(),
                    file.getFilePath(),
                    file.getFileSize(),
                    file.getContentType(),
                    file.getSha256(),
                    file.getStorageKey(),
                    file.getCreatedAt()
            );
        }
    }

    private record SkillSnapshot(
            Long skillId,
            String ownerId,
            Long latestVersionId,
            String displayName,
            String summary,
            Instant updatedAt,
            Long versionId,
            String version,
            SkillVersionStatus status,
            String parsedMetadataJson,
            String manifestJson,
            Instant versionCreatedAt,
            List<FileSnapshot> files) {
    }
}
