package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComplianceMetadataServiceTest {

    private final ComplianceMetadataService service = new ComplianceMetadataService();

    @Test
    void buildsEmptySnapshotWhenFieldIsMissing() {
        ComplianceSnapshot snapshot = service.buildSnapshot(Map.of(), List.of(skillMdEntry()));

        assertEquals("1.0", snapshot.schemaVersion());
        assertTrue(snapshot.items().isEmpty());
        assertTrue(snapshot.digest().startsWith("sha256:"));
    }

    @Test
    void normalizesMappingAndHashesPackagedEvidence() {
        Map<String, Object> frontmatter = Map.of(
                ComplianceMetadataService.FIELD_NAME,
                List.of(Map.of(
                        "standard", "MITRE-ATTACK",
                        "version", "v19.1",
                        "controlId", "T1059",
                        "title", "Command and Scripting Interpreter",
                        "evidence", List.of(
                                Map.of("type", "packaged-file", "path", "references/standards.md"),
                                Map.of("type", "external-url", "url", "https://attack.mitre.org/techniques/T1059/")
                        )
                ))
        );
        PackageEntry standards = new PackageEntry(
                "references/standards.md",
                "MITRE evidence".getBytes(StandardCharsets.UTF_8),
                "MITRE evidence".getBytes(StandardCharsets.UTF_8).length,
                "text/markdown");

        ComplianceSnapshot snapshot = service.buildSnapshot(frontmatter, List.of(skillMdEntry(), standards));

        assertEquals(1, snapshot.items().size());
        ComplianceMapping mapping = snapshot.items().get(0);
        assertEquals("mitre-attack", mapping.standard());
        assertEquals("v19.1", mapping.version());
        assertEquals("T1059", mapping.controlId());
        assertEquals("Command and Scripting Interpreter", mapping.title());
        assertEquals(2, mapping.evidence().size());
        assertEquals("packaged-file", mapping.evidence().get(0).type());
        assertEquals("references/standards.md", mapping.evidence().get(0).path());
        assertEquals("85c516832d12f0c1c86675c2751bd37dcdbdd0573b5a8da74a1bb022089e73d3",
                mapping.evidence().get(0).sha256());
        assertEquals("external-url", mapping.evidence().get(1).type());
        assertEquals("https://attack.mitre.org/techniques/T1059/", mapping.evidence().get(1).url());
        assertTrue(snapshot.digest().startsWith("sha256:"));
    }

    @Test
    void rejectsDuplicateMappings() {
        Map<String, Object> frontmatter = Map.of(
                ComplianceMetadataService.FIELD_NAME,
                List.of(
                        Map.of("standard", "mitre-attack", "version", "v19.1", "controlId", "T1059"),
                        Map.of("standard", "MITRE-ATTACK", "version", "v19.1", "controlId", "T1059")
                )
        );

        DomainBadRequestException exception = assertThrows(
                DomainBadRequestException.class,
                () -> service.buildSnapshot(frontmatter, List.of(skillMdEntry()))
        );

        assertEquals("error.skill.metadata.compliance.invalid", exception.messageCode());
        assertTrue(String.valueOf(exception.messageArgs()[0]).contains("duplicate mapping"));
    }

    @Test
    void rejectsMissingPackagedEvidencePath() {
        Map<String, Object> frontmatter = Map.of(
                ComplianceMetadataService.FIELD_NAME,
                List.of(Map.of(
                        "standard", "nist-csf",
                        "version", "2.0",
                        "controlId", "GV.OC-03",
                        "evidence", List.of(Map.of("type", "packaged-file", "path", "references/missing.md"))
                ))
        );

        List<String> errors = service.validate(frontmatter, List.of(skillMdEntry()));

        assertTrue(errors.stream().anyMatch(error -> error.contains("does not exist in package")));
    }

    @Test
    void rejectsInvalidExternalUrlScheme() {
        Map<String, Object> frontmatter = Map.of(
                ComplianceMetadataService.FIELD_NAME,
                List.of(Map.of(
                        "standard", "soc2",
                        "version", "2017",
                        "controlId", "CC6.1",
                        "evidence", List.of(Map.of("type", "external-url", "url", "file:///tmp/evidence.md"))
                ))
        );

        List<String> errors = service.validate(frontmatter, List.of(skillMdEntry()));

        assertTrue(errors.stream().anyMatch(error -> error.contains("http or https URL")));
    }

    private PackageEntry skillMdEntry() {
        byte[] content = """
                ---
                name: test-skill
                description: Test skill
                version: 1.0.0
                ---
                Body
                """.getBytes(StandardCharsets.UTF_8);
        return new PackageEntry("SKILL.md", content, content.length, "text/markdown");
    }
}
