package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.bootstrap.BuiltinSkillManifestLoader.ManifestItem;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BuiltinSkillInitializerTest {

    private static final String GLOBAL = "global";
    private static final String PUBLISHER = "builtin-skill-publisher";
    private static final byte[] PACKAGE_BYTES = "zip".getBytes(StandardCharsets.UTF_8);
    private static final String PACKAGE_SHA256 =
            "4a70fe9aa6436e02c2dea340fbd1e352e4ef2d8ce6ca52ad25d4b95471fc8bf2";
    private static final ManifestItem ITEM = new ManifestItem(
            "skillhub-hello",
            "1.0.0",
            "https://bjcdn.openstorage.cn/skills/skillhub-hello.zip",
            PACKAGE_SHA256
    );

    @Mock private BuiltinSkillManifestLoader manifestLoader;
    @Mock private BuiltinSkillRemotePackageDownloader downloader;
    @Mock private BuiltinSkillPackageExtractor extractor;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private NamespaceMemberRepository namespaceMemberRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillFileRepository skillFileRepository;
    @Mock private SkillPublishService skillPublishService;

    private BuiltinSkillProperties properties;
    private BuiltinSkillInitializer initializer;
    private Namespace globalNamespace;

    @BeforeEach
    void setUp() {
        properties = new BuiltinSkillProperties();
        initializer = new BuiltinSkillInitializer(
                properties,
                manifestLoader,
                downloader,
                extractor,
                new SkillMetadataParser(),
                namespaceRepository,
                namespaceMemberRepository,
                userAccountRepository,
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                skillPublishService
        );
        globalNamespace = new Namespace(GLOBAL, "Global", "system");
        ReflectionTestUtils.setField(globalNamespace, "id", 1L);
    }

    @Test
    void skipsWhenDisabled() {
        properties.setEnabled(false);

        runInitializer();

        verify(manifestLoader, never()).load();
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsAllItemsWhenGlobalNamespaceDoesNotExist() {
        when(namespaceRepository.findBySlug(GLOBAL)).thenReturn(Optional.empty());

        runInitializer();

        verify(manifestLoader, never()).load();
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void synchronizesAfterApplicationReadyWithoutBlockingApplicationRunner() throws Exception {
        assertThat(ApplicationRunner.class.isAssignableFrom(BuiltinSkillInitializer.class)).isFalse();

        Method method = BuiltinSkillInitializer.class.getDeclaredMethod("synchronizeAfterApplicationReady");
        EventListener eventListener = method.getAnnotation(EventListener.class);
        Async async = method.getAnnotation(Async.class);

        assertThat(eventListener).isNotNull();
        assertThat(eventListener.value()).containsExactly(ApplicationReadyEvent.class);
        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("skillhubEventExecutor");
    }

    @Test
    void skipsSynchronizationWhenPublisherIdIsOccupiedByNonSystemAccount() {
        when(namespaceRepository.findBySlug(GLOBAL)).thenReturn(Optional.of(globalNamespace));
        when(manifestLoader.load()).thenReturn(List.of(ITEM));
        when(userAccountRepository.findById(PUBLISHER))
                .thenReturn(Optional.of(new UserAccount(PUBLISHER, "Human User", "human@example.com", null)));

        runInitializer();

        verify(namespaceMemberRepository, never()).save(any());
        verify(downloader, never()).download(any());
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsPublishedSameVersionBeforeDownloadingPackage() {
        Skill builtinSkill = skill(100L, "skillhub-hello", PUBLISHER);
        SkillVersion published = version(200L, 100L, "1.0.0", SkillVersionStatus.PUBLISHED);
        when(namespaceRepository.findBySlug(GLOBAL)).thenReturn(Optional.of(globalNamespace));
        when(manifestLoader.load()).thenReturn(List.of(ITEM));
        when(userAccountRepository.findById(PUBLISHER)).thenReturn(Optional.of(systemPublisher()));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, PUBLISHER))
                .thenReturn(Optional.of(new NamespaceMember(1L, PUBLISHER, NamespaceRole.OWNER)));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of(builtinSkill));
        when(skillVersionRepository.findBySkillIdAndVersion(100L, "1.0.0")).thenReturn(Optional.of(published));

        runInitializer();

        verify(downloader, never()).download(any());
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsExistingSameVersionWhenNotPublishedBeforeDownloadingPackage() {
        Skill builtinSkill = skill(100L, "skillhub-hello", PUBLISHER);
        SkillVersion uploaded = version(200L, 100L, "1.0.0", SkillVersionStatus.UPLOADED);
        when(namespaceRepository.findBySlug(GLOBAL)).thenReturn(Optional.of(globalNamespace));
        when(manifestLoader.load()).thenReturn(List.of(ITEM));
        when(userAccountRepository.findById(PUBLISHER)).thenReturn(Optional.of(systemPublisher()));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, PUBLISHER))
                .thenReturn(Optional.of(new NamespaceMember(1L, PUBLISHER, NamespaceRole.OWNER)));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of(builtinSkill));
        when(skillVersionRepository.findBySkillIdAndVersion(100L, "1.0.0")).thenReturn(Optional.of(uploaded));

        runInitializer();

        verify(downloader, never()).download(any());
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsSkillOwnedByAnotherUserBeforeDownloadingPackage() {
        Skill otherSkill = skill(100L, "skillhub-hello", "someone-else");
        givenManifestAndSystemPublisher();
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of(otherSkill));

        runInitializer();

        verify(downloader, never()).download(any());
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsSkillOwnedByAnotherUserAfterDownloadingPackage() throws Exception {
        Skill otherSkill = skill(100L, "skillhub-hello", "someone-else");
        givenExtractedPackage(packageEntries("skillhub-hello", "1.0.0", "same"));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello"))
                .thenReturn(List.of())
                .thenReturn(List.of(otherSkill));

        runInitializer();

        verify(downloader).download(URI.create(ITEM.url()));
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsMalformedUrlWithoutSynchronizationFailureLog(CapturedOutput output) {
        ManifestItem malformed = new ManifestItem(
                "skillhub-hello",
                "1.0.0",
                "https://bjcdn.openstorage.cn/skills/%zz.zip",
                PACKAGE_SHA256
        );
        givenManifestAndSystemPublisher(List.of(malformed));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of());

        runInitializer();

        verify(downloader, never()).download(any());
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
        assertThat(output).doesNotContain("Failed to synchronize built-in skill slug=skillhub-hello");
    }

    @Test
    void skipsPackageWithMismatchedSha256BeforeExtraction() throws Exception {
        ManifestItem mismatched = new ManifestItem(
                "skillhub-hello",
                "1.0.0",
                ITEM.url(),
                "0000000000000000000000000000000000000000000000000000000000000000"
        );
        givenManifestAndSystemPublisher(List.of(mismatched));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of());
        when(downloader.download(URI.create(mismatched.url()))).thenReturn(Optional.of(PACKAGE_BYTES));

        runInitializer();

        verify(downloader).download(URI.create(mismatched.url()));
        verify(extractor, never()).extract(any());
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsPackageWhenDownloadFailsBeforeExtraction() throws Exception {
        givenManifestAndSystemPublisher();
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of());
        when(downloader.download(URI.create(ITEM.url()))).thenReturn(Optional.empty());

        runInitializer();

        verify(downloader).download(URI.create(ITEM.url()));
        verify(extractor, never()).extract(any());
        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void continuesWithLaterManifestItemWhenEarlierDownloadFails() throws Exception {
        ManifestItem laterItem = new ManifestItem(
                "next-skill",
                "1.0.0",
                "https://bjcdn.openstorage.cn/skills/next-skill.zip",
                PACKAGE_SHA256
        );
        List<PackageEntry> laterEntries = packageEntries("next-skill", "1.0.0", "same");
        givenManifestAndSystemPublisher(List.of(ITEM, laterItem));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of());
        when(skillRepository.findByNamespaceIdAndSlug(1L, "next-skill")).thenReturn(List.of());
        when(downloader.download(URI.create(ITEM.url()))).thenReturn(Optional.empty());
        when(downloader.download(URI.create(laterItem.url()))).thenReturn(Optional.of(PACKAGE_BYTES));
        when(extractor.extract(PACKAGE_BYTES))
                .thenReturn(new SkillPackageArchiveExtractor.ExtractionResult(laterEntries, List.of()));

        runInitializer();

        verify(downloader).download(URI.create(ITEM.url()));
        verify(downloader).download(URI.create(laterItem.url()));
        verify(skillPublishService).publishFromEntries(
                eq(GLOBAL),
                eq(laterEntries),
                eq(PUBLISHER),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN")),
                eq(true)
        );
    }

    @Test
    void skipsWhenManifestSlugDoesNotMatchPackageMetadata() throws Exception {
        givenExtractedPackage(packageEntries("other-skill", "1.0.0", "same"));

        runInitializer();

        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void skipsWhenManifestVersionDoesNotMatchPackageMetadata() throws Exception {
        givenExtractedPackage(packageEntries("skillhub-hello", "1.0.1", "same"));

        runInitializer();

        verify(skillPublishService, never()).publishFromEntries(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void publishesNewVersionToGlobalAsPublicWithSystemPublisher() throws Exception {
        List<PackageEntry> entries = packageEntries("skillhub-hello", "1.0.0", "same");
        givenExtractedPackage(entries);
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of());

        runInitializer();

        ArgumentCaptor<List<PackageEntry>> entriesCaptor = ArgumentCaptor.captor();
        verify(skillPublishService).publishFromEntries(
                eq(GLOBAL),
                entriesCaptor.capture(),
                eq(PUBLISHER),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN")),
                eq(true)
        );
        assertThat(entriesCaptor.getValue()).isEqualTo(entries);
    }

    @Test
    void createsSystemPublisherAndGlobalMembershipBeforePublishing() throws Exception {
        List<PackageEntry> entries = packageEntries("skillhub-hello", "1.0.0", "same");
        givenExtractedPackage(entries);
        when(userAccountRepository.findById(PUBLISHER)).thenReturn(Optional.empty());
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, PUBLISHER)).thenReturn(Optional.empty());
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello")).thenReturn(List.of());

        runInitializer();

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getId()).isEqualTo(PUBLISHER);
        assertThat(userCaptor.getValue().isSystemAccount()).isTrue();

        ArgumentCaptor<NamespaceMember> memberCaptor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getNamespaceId()).isEqualTo(1L);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(PUBLISHER);
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(NamespaceRole.OWNER);
    }

    @Test
    void treatsConcurrentDuplicatePublishedVersionAsCompleted() throws Exception {
        Skill builtinSkill = skill(100L, "skillhub-hello", PUBLISHER);
        SkillVersion published = version(200L, 100L, "1.0.0", SkillVersionStatus.PUBLISHED);
        List<PackageEntry> entries = packageEntries("skillhub-hello", "1.0.0", "same");
        givenExtractedPackage(entries);
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello"))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(builtinSkill));
        when(skillPublishService.publishFromEntries(
                eq(GLOBAL), any(), eq(PUBLISHER), eq(SkillVisibility.PUBLIC), eq(Set.of("SUPER_ADMIN")), eq(true)))
                .thenThrow(new DomainBadRequestException("error.skill.version.exists", "1.0.0"));
        when(skillVersionRepository.findBySkillIdAndVersion(100L, "1.0.0")).thenReturn(Optional.of(published));
        when(skillFileRepository.findByVersionId(200L)).thenReturn(skillFilesFor(entries, 200L));

        runInitializer();

        verify(skillPublishService).publishFromEntries(
                eq(GLOBAL), any(), eq(PUBLISHER), eq(SkillVisibility.PUBLIC), eq(Set.of("SUPER_ADMIN")), eq(true));
    }

    @Test
    void doesNotTreatConcurrentDuplicateWithDifferentFingerprintAsCompleted(CapturedOutput output) throws Exception {
        Skill builtinSkill = skill(100L, "skillhub-hello", PUBLISHER);
        SkillVersion published = version(200L, 100L, "1.0.0", SkillVersionStatus.PUBLISHED);
        givenExtractedPackage(packageEntries("skillhub-hello", "1.0.0", "new-content"));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "skillhub-hello"))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(builtinSkill));
        when(skillPublishService.publishFromEntries(
                eq(GLOBAL), any(), eq(PUBLISHER), eq(SkillVisibility.PUBLIC), eq(Set.of("SUPER_ADMIN")), eq(true)))
                .thenThrow(new DomainBadRequestException("error.skill.version.exists", "1.0.0"));
        when(skillVersionRepository.findBySkillIdAndVersion(100L, "1.0.0")).thenReturn(Optional.of(published));
        when(skillFileRepository.findByVersionId(200L)).thenReturn(List.of(
                new SkillFile(200L, "SKILL.md", 7L, "text/markdown", sha256("old-content"), "storage-key")
        ));

        runInitializer();

        verify(skillFileRepository).findByVersionId(200L);
        verify(skillPublishService).publishFromEntries(
                eq(GLOBAL), any(), eq(PUBLISHER), eq(SkillVisibility.PUBLIC), eq(Set.of("SUPER_ADMIN")), eq(true));
        assertThat(output).contains("Failed to publish built-in skill slug=skillhub-hello version=1.0.0");
        assertThat(output).doesNotContain("was published concurrently, skipping");
    }

    private void givenExtractedPackage() throws Exception {
        givenExtractedPackage(packageEntries("skillhub-hello", "1.0.0", "same"));
    }

    private void givenExtractedPackage(List<PackageEntry> entries) throws Exception {
        when(namespaceRepository.findBySlug(GLOBAL)).thenReturn(Optional.of(globalNamespace));
        when(manifestLoader.load()).thenReturn(List.of(ITEM));
        lenient().when(userAccountRepository.findById(PUBLISHER)).thenReturn(Optional.of(systemPublisher()));
        lenient().when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, PUBLISHER))
                .thenReturn(Optional.of(new NamespaceMember(1L, PUBLISHER, NamespaceRole.OWNER)));
        when(downloader.download(URI.create(ITEM.url()))).thenReturn(Optional.of(PACKAGE_BYTES));
        when(extractor.extract(PACKAGE_BYTES))
                .thenReturn(new SkillPackageArchiveExtractor.ExtractionResult(entries, List.of()));
    }

    private void givenManifestAndSystemPublisher() {
        givenManifestAndSystemPublisher(List.of(ITEM));
    }

    private void givenManifestAndSystemPublisher(List<ManifestItem> items) {
        when(namespaceRepository.findBySlug(GLOBAL)).thenReturn(Optional.of(globalNamespace));
        when(manifestLoader.load()).thenReturn(items);
        when(userAccountRepository.findById(PUBLISHER)).thenReturn(Optional.of(systemPublisher()));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, PUBLISHER))
                .thenReturn(Optional.of(new NamespaceMember(1L, PUBLISHER, NamespaceRole.OWNER)));
    }

    private void runInitializer() {
        initializer.synchronize();
    }

    private static UserAccount systemPublisher() {
        return UserAccount.systemAccount(PUBLISHER, "Built-in Skill Publisher", null, null);
    }

    private static Skill skill(Long id, String slug, String ownerId) {
        Skill skill = new Skill(1L, slug, ownerId, SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", id);
        return skill;
    }

    private static SkillVersion version(Long id, Long skillId, String version, SkillVersionStatus status) {
        SkillVersion skillVersion = new SkillVersion(skillId, version, PUBLISHER);
        ReflectionTestUtils.setField(skillVersion, "id", id);
        skillVersion.setStatus(status);
        return skillVersion;
    }

    private static List<PackageEntry> packageEntries(String name, String version, String readme) {
        byte[] skillMd = ("""
                ---
                name: %s
                description: Built-in guardrails
                version: %s
                ---
                # %s
                """).formatted(name, version, name).getBytes(StandardCharsets.UTF_8);
        byte[] readmeBytes = readme.getBytes(StandardCharsets.UTF_8);
        return List.of(
                new PackageEntry("SKILL.md", skillMd, skillMd.length, "text/markdown"),
                new PackageEntry("README.md", readmeBytes, readmeBytes.length, "text/markdown")
        );
    }

    private static List<SkillFile> skillFilesFor(List<PackageEntry> entries, Long versionId) {
        return entries.stream()
                .map(entry -> new SkillFile(
                        versionId,
                        entry.path(),
                        entry.size(),
                        entry.contentType(),
                        sha256(entry.content()),
                        "storage-key/" + entry.path()
                ))
                .toList();
    }

    private static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
