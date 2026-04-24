package com.iflytek.skillhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.service.support.SkillPackageArchiveExtractor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SkillPublishAppServiceTest {

    @Mock
    private SkillPublishService skillPublishService;
    @Mock
    private SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    @Mock
    private SkillHubMetrics skillHubMetrics;

    @Test
    void publish_discoversMultipleSkillDirectoriesAndPublishesInOneBatch() throws Exception {
        SkillPublishAppService service = newService();
        MockMultipartFile file = upload();
        given(skillPackageArchiveExtractor.extract(file)).willReturn(List.of(
                entry("alpha/SKILL.md", skillMd("Alpha Skill", "1.0.0")),
                entry("alpha/README.md", "alpha readme"),
                entry("beta/SKILL.md", skillMd("Beta Skill", "2.0.0")),
                entry("beta/src/main.py", "print('beta')\n")
        ));
        given(skillPublishService.publishFromEntries(
                eq("global"),
                anyList(),
                eq("usr_1"),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN")),
                eq(true)))
                .willReturn(
                        new SkillPublishService.PublishResult(101L, "alpha-skill",
                                version("1.0.0", SkillVersionStatus.PENDING_REVIEW, 2, 64L)),
                        new SkillPublishService.PublishResult(102L, "beta-skill",
                                version("2.0.0", SkillVersionStatus.PENDING_REVIEW, 2, 96L))
                );

        PublishResponse response = service.publish(
                "global",
                file,
                SkillVisibility.PUBLIC,
                "usr_1",
                Set.of("SUPER_ADMIN"),
                true
        );

        assertEquals(101L, response.skillId());
        assertEquals("alpha-skill", response.slug());
        assertEquals(2, response.results().size());
        assertEquals("alpha", response.results().get(0).packagePath());
        assertEquals("beta", response.results().get(1).packagePath());
        assertEquals("beta-skill", response.results().get(1).slug());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PackageEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(skillPublishService, org.mockito.Mockito.times(2)).publishFromEntries(
                eq("global"),
                entriesCaptor.capture(),
                eq("usr_1"),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of("SUPER_ADMIN")),
                eq(true)
        );
        assertEquals(List.of("SKILL.md", "README.md"), paths(entriesCaptor.getAllValues().get(0)));
        assertEquals(List.of("SKILL.md", "src/main.py"), paths(entriesCaptor.getAllValues().get(1)));
        verify(skillHubMetrics, org.mockito.Mockito.times(2)).incrementSkillPublish("global", "PENDING_REVIEW");
    }

    @Test
    void publish_usesSingleRootPackageUnchangedForCompatibility() throws Exception {
        SkillPublishAppService service = newService();
        MockMultipartFile file = upload();
        given(skillPackageArchiveExtractor.extract(file)).willReturn(List.of(
                entry("SKILL.md", skillMd("Solo Skill", "1.0.0")),
                entry("README.md", "solo readme")
        ));
        given(skillPublishService.publishFromEntries(
                eq("global"),
                anyList(),
                eq("usr_1"),
                eq(SkillVisibility.PRIVATE),
                eq(Set.of()),
                eq(false)))
                .willReturn(new SkillPublishService.PublishResult(201L, "solo-skill",
                        version("1.0.0", SkillVersionStatus.UPLOADED, 2, 32L)));

        PublishResponse response = service.publish(
                "global",
                file,
                SkillVisibility.PRIVATE,
                "usr_1",
                Set.of(),
                false
        );

        assertEquals(201L, response.skillId());
        assertEquals("solo-skill", response.slug());
        assertEquals(1, response.results().size());
        assertEquals("", response.results().get(0).packagePath());
    }

    @Test
    void publish_ignoresCollectionFilesOutsideDiscoveredSkillDirectories() throws Exception {
        SkillPublishAppService service = newService();
        MockMultipartFile file = upload();
        given(skillPackageArchiveExtractor.extract(file)).willReturn(List.of(
                entry("alpha/SKILL.md", skillMd("Alpha Skill", "1.0.0")),
                entry("README.md", "collection readme")
        ));
        given(skillPublishService.publishFromEntries(
                eq("global"),
                anyList(),
                eq("usr_1"),
                eq(SkillVisibility.PUBLIC),
                eq(Set.of()),
                eq(false)))
                .willReturn(new SkillPublishService.PublishResult(301L, "alpha-skill",
                        version("1.0.0", SkillVersionStatus.PENDING_REVIEW, 1, 24L)));

        PublishResponse response = service.publish(
                "global",
                file,
                SkillVisibility.PUBLIC,
                "usr_1",
                Set.of(),
                false
        );

        assertEquals(1, response.results().size());
        assertEquals("alpha-skill", response.results().get(0).slug());
    }

    @Test
    void publishMethod_isTransactionalSoBatchPublishesShareOneTransaction() throws Exception {
        Method method = SkillPublishAppService.class.getMethod(
                "publish",
                String.class,
                org.springframework.web.multipart.MultipartFile.class,
                SkillVisibility.class,
                String.class,
                Set.class,
                boolean.class
        );
        assertNotNull(method.getAnnotation(Transactional.class));
    }

    private SkillPublishAppService newService() {
        return new SkillPublishAppService(skillPublishService, skillPackageArchiveExtractor, skillHubMetrics);
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "skills.zip", "application/zip", new byte[] {1, 2, 3});
    }

    private PackageEntry entry(String path, String content) {
        return new PackageEntry(path, content.getBytes(StandardCharsets.UTF_8), content.length(), "text/markdown");
    }

    private String skillMd(String name, String version) {
        return """
                ---
                name: %s
                version: %s
                ---
                """.formatted(name, version);
    }

    private SkillVersion version(String value, SkillVersionStatus status, int fileCount, long totalSize) {
        SkillVersion version = new SkillVersion(1L, value, "usr_1");
        version.setStatus(status);
        version.setFileCount(fileCount);
        version.setTotalSize(totalSize);
        return version;
    }

    private List<String> paths(List<PackageEntry> entries) {
        return entries.stream().map(PackageEntry::path).toList();
    }
}
