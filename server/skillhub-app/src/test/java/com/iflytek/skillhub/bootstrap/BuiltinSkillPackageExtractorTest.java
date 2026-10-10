package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class BuiltinSkillPackageExtractorTest {

    private final BuiltinSkillPackageExtractor extractor = new BuiltinSkillPackageExtractor(
            new SkillPackageArchiveExtractor(new SkillPublishProperties())
    );

    @Test
    void extractsZipBytesThroughArchiveExtractor() throws Exception {
        byte[] zip = zip(
                entry("SKILL.md", """
                        ---
                        name: skillhub-hello
                        version: 1.0.0
                        ---
                        # SkillHub Hello
                        """),
                entry("README.md", "# Readme")
        );

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extract(zip);

        assertThat(result.entries())
                .extracting(entry -> entry.path())
                .containsExactly("SKILL.md", "README.md");
    }

    @Test
    void rejectsZipWithoutRootSkillMd() throws Exception {
        byte[] zip = zip(entry("README.md", "# Readme"));

        assertThatThrownBy(() -> extractor.extract(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkillPackagePolicy.SKILL_MD_PATH);
    }

    @Test
    void acceptsZipWithSingleTopLevelSkillDirectory() throws Exception {
        byte[] zip = zip(entry("skillhub-hello/SKILL.md", """
                ---
                name: skillhub-hello
                version: 1.0.0
                ---
                # SkillHub Hello
                """), entry("skillhub-hello/README.md", "# Readme"));

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extract(zip);

        assertThat(result.entries())
                .extracting(entry -> entry.path())
                .containsExactly("SKILL.md", "README.md");
    }

    @Test
    void rejectsZipWhenSkillDirectoryPromotionWouldIgnoreOutsideFiles() throws Exception {
        byte[] zip = zip(entry("skillhub-hello/SKILL.md", """
                ---
                name: skillhub-hello
                version: 1.0.0
                ---
                # SkillHub Hello
                """), entry("LICENSE", "Apache-2.0"));

        assertThatThrownBy(() -> extractor.extract(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ignored file outside skill directory: LICENSE");
    }

    private static ZipSource entry(String path, String content) {
        return new ZipSource(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zip(ZipSource... sources) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (ZipSource source : sources) {
                zipOutputStream.putNextEntry(new ZipEntry(source.path()));
                zipOutputStream.write(source.content());
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    record ZipSource(String path, byte[] content) {
    }
}
