package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.metadata.ComplianceMetadataService;
import com.iflytek.skillhub.dto.ComplianceEvidenceResponse;
import com.iflytek.skillhub.dto.ComplianceMappingResponse;
import com.iflytek.skillhub.dto.ComplianceSnapshotResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ComplianceSnapshotProjectionService {

    private final ObjectMapper objectMapper;

    public ComplianceSnapshotProjectionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ComplianceSnapshotResponse fromParsedMetadataJson(String parsedMetadataJson) {
        if (parsedMetadataJson == null || parsedMetadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(parsedMetadataJson);
            JsonNode snapshot = root.path(ComplianceMetadataService.SNAPSHOT_FIELD_NAME);
            if (!snapshot.isObject()) {
                return null;
            }
            return toSnapshot(snapshot);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ComplianceSnapshotResponse toSnapshot(JsonNode snapshot) {
        return new ComplianceSnapshotResponse(
                textOrNull(snapshot.path("schemaVersion")),
                toMappings(snapshot.path("items")),
                textOrNull(snapshot.path("digest"))
        );
    }

    private List<ComplianceMappingResponse> toMappings(JsonNode items) {
        if (!items.isArray()) {
            return List.of();
        }
        List<ComplianceMappingResponse> mappings = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isObject()) {
                continue;
            }
            mappings.add(new ComplianceMappingResponse(
                    textOrNull(item.path("standard")),
                    textOrNull(item.path("version")),
                    textOrNull(item.path("controlId")),
                    textOrNull(item.path("title")),
                    toEvidence(item.path("evidence"))
            ));
        }
        return List.copyOf(mappings);
    }

    private List<ComplianceEvidenceResponse> toEvidence(JsonNode evidenceItems) {
        if (!evidenceItems.isArray()) {
            return List.of();
        }
        List<ComplianceEvidenceResponse> evidence = new ArrayList<>();
        for (JsonNode item : evidenceItems) {
            if (!item.isObject()) {
                continue;
            }
            evidence.add(new ComplianceEvidenceResponse(
                    textOrNull(item.path("type")),
                    textOrNull(item.path("path")),
                    textOrNull(item.path("url")),
                    textOrNull(item.path("sha256"))
            ));
        }
        return List.copyOf(evidence);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
