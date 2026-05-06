package com.iflytek.skillhub.domain.skill.validation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillPackagePolicyTest {

    // --- normalizeEntryPath ---

    @Test
    void normalizeEntryPath_nullThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Package entry path is missing");
    }

    @Test
    void normalizeEntryPath_emptyThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Package entry path is empty");
    }

    @Test
    void normalizeEntryPath_leadingSlashThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be relative");
    }

    @Test
    void normalizeEntryPath_leadingBackslashThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("\\Windows\\System32"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be relative");
    }

    @Test
    void normalizeEntryPath_containsColonThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("C:\\file.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid drive or scheme prefix");
    }

    @Test
    void normalizeEntryPath_dotThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is invalid");
    }

    @Test
    void normalizeEntryPath_dotDotThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath(".."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes package root");
    }

    @Test
    void normalizeEntryPath_traversalThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes package root");
    }

    @Test
    void normalizeEntryPath_notNormalizedThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("foo/../bar.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be normalized");
    }

    @Test
    void normalizeEntryPath_blankAfterNormalizationThrows() {
        assertThatThrownBy(() -> SkillPackagePolicy.normalizeEntryPath("foo/.."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is invalid");
    }

    @Test
    void normalizeEntryPath_validPath() {
        assertThat(SkillPackagePolicy.normalizeEntryPath("docs/guide.md")).isEqualTo("docs/guide.md");
    }

    @Test
    void normalizeEntryPath_backslashConverted() {
        assertThat(SkillPackagePolicy.normalizeEntryPath("docs\\guide.md")).isEqualTo("docs/guide.md");
    }

    @Test
    void normalizeEntryPath_trimsWhitespace() {
        assertThat(SkillPackagePolicy.normalizeEntryPath("  docs/guide.md  ")).isEqualTo("docs/guide.md");
    }

    // --- hasAllowedExtension ---

    @Test
    void hasAllowedExtension_trueForAllowed() {
        assertThat(SkillPackagePolicy.hasAllowedExtension("readme.md")).isTrue();
    }

    @Test
    void hasAllowedExtension_falseForDisallowed() {
        assertThat(SkillPackagePolicy.hasAllowedExtension("virus.exe")).isFalse();
    }

    // --- validateContentMatchesExtension ---

    @Test
    void validateContentMatchesExtension_nullForUnknownExtension() {
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("data.bin", new byte[]{0x00})).isNull();
    }

    @Test
    void validateContentMatchesExtension_nullForTextExtension() {
        byte[] utf8 = "hello world".getBytes(StandardCharsets.UTF_8);
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("readme.txt", utf8)).isNull();
    }

    @Test
    void validateContentMatchesExtension_pngValid() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.png", png)).isNull();
    }

    @Test
    void validateContentMatchesExtension_pngInvalid() {
        byte[] notPng = new byte[]{0x00, 0x00, 0x00, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.png", notPng))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_jpgValid() {
        byte[] jpg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.jpg", jpg)).isNull();
    }

    @Test
    void validateContentMatchesExtension_jpgInvalid() {
        byte[] notJpg = new byte[]{0x00, 0x00, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.jpg", notJpg))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_svgValid() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("icon.svg", svg)).isNull();
    }

    @Test
    void validateContentMatchesExtension_svgInvalidMissingSvgTag() {
        byte[] notSvg = "not really svg".getBytes(StandardCharsets.UTF_8);
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("icon.svg", notSvg))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_svgInvalidBinary() {
        byte[] binary = new byte[]{0x00, 0x01, 0x02};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("icon.svg", binary))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_jpegValid() {
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.jpeg", jpeg)).isNull();
    }

    @Test
    void validateContentMatchesExtension_jpegInvalid() {
        byte[] notJpeg = new byte[]{0x00, 0x00, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.jpeg", notJpeg))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_gifValid() {
        byte[] gif = "GIF89a".getBytes(StandardCharsets.UTF_8);
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("anim.gif", gif)).isNull();
    }

    @Test
    void validateContentMatchesExtension_gifInvalid() {
        byte[] notGif = new byte[]{0x00, 0x00, 0x00, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("anim.gif", notGif))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_webpValid() {
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.webp", webp)).isNull();
    }

    @Test
    void validateContentMatchesExtension_webpInvalidTooShort() {
        byte[] shortBytes = new byte[]{0x00, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.webp", shortBytes))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_webpInvalidWrongMagic() {
        byte[] wrong = new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'X'};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("img.webp", wrong))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_icoValid() {
        byte[] ico = new byte[]{0x00, 0x00, 0x01, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("favicon.ico", ico)).isNull();
    }

    @Test
    void validateContentMatchesExtension_icoInvalid() {
        byte[] notIco = new byte[]{0x00, 0x00, 0x00, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("favicon.ico", notIco))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_pdfValid() {
        byte[] pdf = "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8);
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("doc.pdf", pdf)).isNull();
    }

    @Test
    void validateContentMatchesExtension_pdfInvalid() {
        byte[] notPdf = new byte[]{0x00, 0x00, 0x00, 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("doc.pdf", notPdf))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_textExtensionInvalidUtf8() {
        byte[] invalidUtf8 = new byte[]{(byte) 0xff, (byte) 0xfe};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("readme.md", invalidUtf8))
                .contains("File content does not match extension");
    }

    @Test
    void validateContentMatchesExtension_textExtensionWithNullByte() {
        byte[] withNull = new byte[]{'h', 'e', 'l', 'l', 'o', 0x00};
        assertThat(SkillPackagePolicy.validateContentMatchesExtension("readme.md", withNull))
                .contains("File content does not match extension");
    }
}
