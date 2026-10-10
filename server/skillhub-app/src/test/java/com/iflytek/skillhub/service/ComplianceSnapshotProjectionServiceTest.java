package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceSnapshotProjectionServiceTest {

    private final ComplianceSnapshotProjectionService service =
            new ComplianceSnapshotProjectionService(new ObjectMapper());

    @Test
    void returnsNullWhenParsedMetadataIsBlankOrLegacy() {
        assertThat(service.fromParsedMetadataJson(null)).isNull();
        assertThat(service.fromParsedMetadataJson("")).isNull();
        assertThat(service.fromParsedMetadataJson("{\"name\":\"legacy\"}")).isNull();
    }

    @Test
    void returnsNullWhenParsedMetadataIsInvalidJson() {
        assertThat(service.fromParsedMetadataJson("{not-json")).isNull();
    }

    @Test
    void projectsComplianceSnapshotFromParsedMetadata() {
        var snapshot = service.fromParsedMetadataJson("""
                {
                  "name": "demo",
                  "complianceSnapshot": {
                    "schemaVersion": "1.0",
                    "items": [
                      {
                        "standard": "mitre-attack",
                        "version": "v19.1",
                        "controlId": "T1059",
                        "title": "Command and Scripting Interpreter",
                        "evidence": [
                          {
                            "type": "packaged-file",
                            "path": "references/standards.md",
                            "sha256": "85c516"
                          }
                        ]
                      }
                    ],
                    "digest": "sha256:demo"
                  }
                }
                """);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.schemaVersion()).isEqualTo("1.0");
        assertThat(snapshot.digest()).isEqualTo("sha256:demo");
        assertThat(snapshot.items()).hasSize(1);
        assertThat(snapshot.items().get(0).controlId()).isEqualTo("T1059");
        assertThat(snapshot.items().get(0).evidence().get(0).path()).isEqualTo("references/standards.md");
    }
}
