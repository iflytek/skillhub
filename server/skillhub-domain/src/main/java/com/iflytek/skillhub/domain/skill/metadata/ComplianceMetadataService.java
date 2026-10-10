package com.iflytek.skillhub.domain.skill.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;

import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts, validates, and snapshots SkillHub/Astron compliance metadata from SKILL.md frontmatter.
 */
public class ComplianceMetadataService {

    public static final String FIELD_NAME = "x-astron-compliance";
    public static final String SNAPSHOT_FIELD_NAME = "complianceSnapshot";

    private static final String EVIDENCE_TYPE_PACKAGED_FILE = "packaged-file";
    private static final String EVIDENCE_TYPE_EXTERNAL_URL = "external-url";
    private static final int MAX_MAPPINGS = 50;
    private static final int MAX_EVIDENCE_ITEMS = 10;
    private static final int MAX_STANDARD_LENGTH = 64;
    private static final int MAX_VERSION_LENGTH = 64;
    private static final int MAX_CONTROL_ID_LENGTH = 128;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final Pattern STANDARD_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Pattern CONTROL_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");

    private final ObjectMapper digestObjectMapper;

    public ComplianceMetadataService() {
        this.digestObjectMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public List<String> validate(Map<String, Object> frontmatter, List<PackageEntry> entries) {
        return parse(frontmatter, entries).errors();
    }

    public ComplianceSnapshot buildSnapshot(Map<String, Object> frontmatter, List<PackageEntry> entries) {
        ParseResult result = parse(frontmatter, entries);
        if (!result.errors().isEmpty()) {
            throw new DomainBadRequestException(
                    "error.skill.metadata.compliance.invalid",
                    String.join(", ", result.errors()));
        }
        return snapshot(result.mappings());
    }

    private ParseResult parse(Map<String, Object> frontmatter, List<PackageEntry> entries) {
        Object rawMappings = frontmatter.get(FIELD_NAME);
        if (rawMappings == null) {
            return new ParseResult(List.of(), List.of());
        }

        List<String> errors = new ArrayList<>();
        if (!(rawMappings instanceof List<?> mappingItems)) {
            return new ParseResult(List.of(), List.of(FIELD_NAME + " must be an array"));
        }
        if (mappingItems.size() > MAX_MAPPINGS) {
            errors.add(FIELD_NAME + " must contain at most " + MAX_MAPPINGS + " items");
        }

        Map<String, PackageEntry> packageEntries = packageEntryMap(entries);
        Set<String> seenMappings = new LinkedHashSet<>();
        List<ComplianceMapping> mappings = new ArrayList<>();

        for (int index = 0; index < mappingItems.size(); index++) {
            Object rawItem = mappingItems.get(index);
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                errors.add(FIELD_NAME + "[" + index + "] must be an object");
                continue;
            }
            Map<String, Object> item = normalizeMap(rawMap);
            validateAllowedFields(item, index, Set.of("standard", "version", "controlId", "title", "evidence"), errors);

            String standard = requiredString(item, index, "standard", MAX_STANDARD_LENGTH, errors);
            String standardNormalized = standard == null ? null : standard.toLowerCase(Locale.ROOT);
            if (standardNormalized != null && !STANDARD_PATTERN.matcher(standardNormalized).matches()) {
                errors.add(FIELD_NAME + "[" + index + "].standard has an invalid format");
            }

            String version = requiredString(item, index, "version", MAX_VERSION_LENGTH, errors);
            String controlId = requiredString(item, index, "controlId", MAX_CONTROL_ID_LENGTH, errors);
            if (controlId != null && !CONTROL_ID_PATTERN.matcher(controlId).matches()) {
                errors.add(FIELD_NAME + "[" + index + "].controlId has an invalid format");
            }
            String title = optionalString(item, index, "title", MAX_TITLE_LENGTH, errors);
            List<ComplianceEvidence> evidence = parseEvidence(item.get("evidence"), index, packageEntries, errors);

            if (standardNormalized != null && version != null && controlId != null) {
                String duplicateKey = standardNormalized + "\u0000" + version + "\u0000" + controlId;
                if (!seenMappings.add(duplicateKey)) {
                    errors.add(FIELD_NAME + " contains duplicate mapping "
                            + standardNormalized + "/" + version + "/" + controlId);
                    continue;
                }
                mappings.add(new ComplianceMapping(
                        standardNormalized,
                        version,
                        controlId,
                        title,
                        List.copyOf(evidence)));
            }
        }

        return new ParseResult(List.copyOf(mappings), List.copyOf(errors));
    }

    private List<ComplianceEvidence> parseEvidence(Object rawEvidence,
                                                   int mappingIndex,
                                                   Map<String, PackageEntry> packageEntries,
                                                   List<String> errors) {
        if (rawEvidence == null) {
            return List.of();
        }
        if (!(rawEvidence instanceof List<?> evidenceItems)) {
            errors.add(FIELD_NAME + "[" + mappingIndex + "].evidence must be an array");
            return List.of();
        }
        if (evidenceItems.size() > MAX_EVIDENCE_ITEMS) {
            errors.add(FIELD_NAME + "[" + mappingIndex + "].evidence must contain at most "
                    + MAX_EVIDENCE_ITEMS + " items");
        }

        List<ComplianceEvidence> evidence = new ArrayList<>();
        for (int evidenceIndex = 0; evidenceIndex < evidenceItems.size(); evidenceIndex++) {
            Object rawItem = evidenceItems.get(evidenceIndex);
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                errors.add(evidencePath(mappingIndex, evidenceIndex) + " must be an object");
                continue;
            }
            Map<String, Object> item = normalizeMap(rawMap);
            validateAllowedFields(item, mappingIndex, evidenceIndex, Set.of("type", "path", "url"), errors);
            String type = requiredEvidenceString(item, mappingIndex, evidenceIndex, "type", 32, errors);
            if (type == null) {
                continue;
            }
            String normalizedType = type.toLowerCase(Locale.ROOT);
            if (EVIDENCE_TYPE_PACKAGED_FILE.equals(normalizedType)) {
                ComplianceEvidence packagedEvidence = parsePackagedFileEvidence(
                        item, mappingIndex, evidenceIndex, packageEntries, errors);
                if (packagedEvidence != null) {
                    evidence.add(packagedEvidence);
                }
            } else if (EVIDENCE_TYPE_EXTERNAL_URL.equals(normalizedType)) {
                ComplianceEvidence externalEvidence = parseExternalUrlEvidence(
                        item, mappingIndex, evidenceIndex, errors);
                if (externalEvidence != null) {
                    evidence.add(externalEvidence);
                }
            } else {
                errors.add(evidencePath(mappingIndex, evidenceIndex)
                        + ".type must be one of packaged-file, external-url");
            }
        }
        return evidence;
    }

    private ComplianceEvidence parsePackagedFileEvidence(Map<String, Object> item,
                                                         int mappingIndex,
                                                         int evidenceIndex,
                                                         Map<String, PackageEntry> packageEntries,
                                                         List<String> errors) {
        String rawPath = requiredEvidenceString(item, mappingIndex, evidenceIndex, "path", 512, errors);
        if (rawPath == null) {
            return null;
        }
        String normalizedPath;
        try {
            normalizedPath = SkillPackagePolicy.normalizeEntryPath(rawPath);
        } catch (IllegalArgumentException exception) {
            errors.add(evidencePath(mappingIndex, evidenceIndex) + ".path " + exception.getMessage());
            return null;
        }
        PackageEntry entry = packageEntries.get(normalizedPath);
        if (entry == null) {
            errors.add(evidencePath(mappingIndex, evidenceIndex)
                    + ".path does not exist in package: " + normalizedPath);
            return null;
        }
        return new ComplianceEvidence(
                EVIDENCE_TYPE_PACKAGED_FILE,
                normalizedPath,
                null,
                sha256(entry.content()));
    }

    private ComplianceEvidence parseExternalUrlEvidence(Map<String, Object> item,
                                                        int mappingIndex,
                                                        int evidenceIndex,
                                                        List<String> errors) {
        String url = requiredEvidenceString(item, mappingIndex, evidenceIndex, "url", 2048, errors);
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                errors.add(evidencePath(mappingIndex, evidenceIndex) + ".url must be an http or https URL");
                return null;
            }
        } catch (IllegalArgumentException exception) {
            errors.add(evidencePath(mappingIndex, evidenceIndex) + ".url must be an http or https URL");
            return null;
        }
        return new ComplianceEvidence(EVIDENCE_TYPE_EXTERNAL_URL, null, url, null);
    }

    private ComplianceSnapshot snapshot(List<ComplianceMapping> mappings) {
        Map<String, Object> payload = Map.of(
                "schemaVersion", ComplianceSnapshot.CURRENT_SCHEMA_VERSION,
                "items", mappings.stream().map(this::mappingPayload).toList());
        String digest = "sha256:" + sha256(toJsonBytes(payload));
        return new ComplianceSnapshot(ComplianceSnapshot.CURRENT_SCHEMA_VERSION, List.copyOf(mappings), digest);
    }

    private Map<String, Object> mappingPayload(ComplianceMapping mapping) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("standard", mapping.standard());
        payload.put("version", mapping.version());
        payload.put("controlId", mapping.controlId());
        if (mapping.title() != null) {
            payload.put("title", mapping.title());
        }
        payload.put("evidence", mapping.evidence().stream().map(this::evidencePayload).toList());
        return payload;
    }

    private Map<String, Object> evidencePayload(ComplianceEvidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", evidence.type());
        if (evidence.path() != null) {
            payload.put("path", evidence.path());
        }
        if (evidence.url() != null) {
            payload.put("url", evidence.url());
        }
        if (evidence.sha256() != null) {
            payload.put("sha256", evidence.sha256());
        }
        return payload;
    }

    private Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private Map<String, PackageEntry> packageEntryMap(List<PackageEntry> entries) {
        Map<String, PackageEntry> packageEntries = new LinkedHashMap<>();
        for (PackageEntry entry : entries) {
            try {
                packageEntries.putIfAbsent(SkillPackagePolicy.normalizeEntryPath(entry.path()), entry);
            } catch (IllegalArgumentException ignored) {
                // Structural path errors are reported by SkillPackageValidator. Compliance validation should
                // continue so the caller can see all package problems in one response.
            }
        }
        return packageEntries;
    }

    private String requiredString(Map<String, Object> item,
                                  int index,
                                  String fieldName,
                                  int maxLength,
                                  List<String> errors) {
        Object rawValue = item.get(fieldName);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            errors.add(FIELD_NAME + "[" + index + "]." + fieldName + " is required");
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            errors.add(FIELD_NAME + "[" + index + "]." + fieldName
                    + " must be at most " + maxLength + " characters");
            return null;
        }
        return trimmed;
    }

    private String optionalString(Map<String, Object> item,
                                  int index,
                                  String fieldName,
                                  int maxLength,
                                  List<String> errors) {
        Object rawValue = item.get(fieldName);
        if (rawValue == null) {
            return null;
        }
        if (!(rawValue instanceof String value) || value.isBlank()) {
            errors.add(FIELD_NAME + "[" + index + "]." + fieldName + " must be a non-empty string");
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            errors.add(FIELD_NAME + "[" + index + "]." + fieldName
                    + " must be at most " + maxLength + " characters");
            return null;
        }
        return trimmed;
    }

    private String requiredEvidenceString(Map<String, Object> item,
                                          int mappingIndex,
                                          int evidenceIndex,
                                          String fieldName,
                                          int maxLength,
                                          List<String> errors) {
        Object rawValue = item.get(fieldName);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            errors.add(evidencePath(mappingIndex, evidenceIndex) + "." + fieldName + " is required");
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            errors.add(evidencePath(mappingIndex, evidenceIndex) + "." + fieldName
                    + " must be at most " + maxLength + " characters");
            return null;
        }
        return trimmed;
    }

    private void validateAllowedFields(Map<String, Object> item,
                                       int mappingIndex,
                                       Set<String> allowedFields,
                                       List<String> errors) {
        for (String fieldName : item.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                errors.add(FIELD_NAME + "[" + mappingIndex + "]." + fieldName + " is not allowed");
            }
        }
    }

    private void validateAllowedFields(Map<String, Object> item,
                                       int mappingIndex,
                                       int evidenceIndex,
                                       Set<String> allowedFields,
                                       List<String> errors) {
        for (String fieldName : item.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                errors.add(evidencePath(mappingIndex, evidenceIndex) + "." + fieldName + " is not allowed");
            }
        }
    }

    private String evidencePath(int mappingIndex, int evidenceIndex) {
        return FIELD_NAME + "[" + mappingIndex + "].evidence[" + evidenceIndex + "]";
    }

    private byte[] toJsonBytes(Map<String, Object> payload) {
        try {
            return digestObjectMapper.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize compliance snapshot", exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to calculate SHA-256", exception);
        }
    }

    private record ParseResult(List<ComplianceMapping> mappings, List<String> errors) {}
}
