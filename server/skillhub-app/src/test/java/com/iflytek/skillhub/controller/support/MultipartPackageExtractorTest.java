package com.iflytek.skillhub.controller.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipartPackageExtractorTest {

    @Test
    void extractCanonicalizesCaseInsensitiveSkillMd() throws Exception {
        MultipartPackageExtractor extractor = new MultipartPackageExtractor(
                new SkillPublishProperties(),
                new ObjectMapper()
        );
        MockMultipartFile skillMd = new MockMultipartFile(
                "files",
                "skill.md",
                "text/markdown",
                "---\nname: test\n---\n".getBytes()
        );

        MultipartPackageExtractor.ExtractedPackage extracted = extractor.extract(
                new MockMultipartFile[] {skillMd},
                "{\"namespace\":\"global\",\"slug\":\"test\"}"
        );

        assertEquals(1, extracted.entries().size());
        assertTrue(extracted.entries().stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(extracted.entries().stream().noneMatch(e -> e.path().equals("skill.md")));
    }
}
