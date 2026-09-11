package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, side-effect-free parser for the root {@code SUITE.yaml} Bundle manifest. */
public class SkillSuiteBundleManifestParser {

    private static final int MAX_YAML_ALIASES = 20;
    private static final int MAX_YAML_NESTING_DEPTH = 20;
    private static final int MAX_YAML_CODE_POINTS = 256_000;
    private static final int MAX_MEMBERS = 100;
    private static final Pattern PORTABLE_VERSION_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");

    private static final Set<String> ROOT_FIELDS = Set.of("apiVersion", "kind", "metadata", "spec");
    private static final Set<String> METADATA_FIELDS = Set.of("namespace", "slug");
    private static final Set<String> SPEC_FIELDS = Set.of(
            "mode", "baseVersion", "version", "displayName", "summary", "overview",
            "visibility", "changelog", "entry", "members");
    private static final Set<String> MEMBER_FIELDS = Set.of("skill", "package", "reference");
    private static final Set<String> PACKAGE_FIELDS = Set.of("path", "visibility");
    private static final Set<String> REFERENCE_FIELDS = Set.of("version");

    public SkillSuiteBundleManifest parse(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw invalid("manifest is empty");
        }

        Map<?, ?> root = parseYaml(yamlContent);
        rejectUnknownFields(root, ROOT_FIELDS, "manifest");
        String apiVersion = requiredString(root, "apiVersion", "manifest");
        String kind = requiredString(root, "kind", "manifest");
        if (!SkillSuiteBundleManifest.API_VERSION.equals(apiVersion)) {
            throw invalid("unsupported apiVersion: " + apiVersion);
        }
        if (!SkillSuiteBundleManifest.KIND.equals(kind)) {
            throw invalid("kind must be " + SkillSuiteBundleManifest.KIND);
        }

        SkillSuiteBundleManifest.Metadata metadata = parseMetadata(requiredMap(root, "metadata", "manifest"));
        SkillSuiteBundleManifest.Spec spec = parseSpec(requiredMap(root, "spec", "manifest"));
        return new SkillSuiteBundleManifest(apiVersion, kind, metadata, spec);
    }

    private Map<?, ?> parseYaml(String yamlContent) {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(MAX_YAML_ALIASES);
            options.setNestingDepthLimit(MAX_YAML_NESTING_DEPTH);
            options.setCodePointLimit(MAX_YAML_CODE_POINTS);
            Object parsed = new Yaml(new SafeConstructor(options)).load(yamlContent);
            if (!(parsed instanceof Map<?, ?> map)) {
                throw invalid("manifest must be a YAML object");
            }
            return map;
        } catch (DomainBadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("invalid YAML: " + safeMessage(exception));
        }
    }

    private SkillSuiteBundleManifest.Metadata parseMetadata(Map<?, ?> map) {
        rejectUnknownFields(map, METADATA_FIELDS, "metadata");
        String namespace = requiredString(map, "namespace", "metadata");
        String slug = requiredString(map, "slug", "metadata");
        return new SkillSuiteBundleManifest.Metadata(coordinate(namespace, slug, "metadata"));
    }

    private SkillSuiteBundleManifest.Spec parseSpec(Map<?, ?> map) {
        rejectUnknownFields(map, SPEC_FIELDS, "spec");
        SkillSuiteBundleMode mode = enumValue(
                requiredString(map, "mode", "spec"), SkillSuiteBundleMode.class, "spec.mode");
        String baseVersion = optionalString(map, "baseVersion", "spec");
        if (mode == SkillSuiteBundleMode.UPDATE && baseVersion == null) {
            throw invalid("spec.baseVersion is required for UPDATE");
        }
        if (mode == SkillSuiteBundleMode.CREATE && baseVersion != null) {
            throw invalid("spec.baseVersion is not allowed for CREATE");
        }
        validateVersion(baseVersion, "spec.baseVersion");

        String version = requiredString(map, "version", "spec");
        validateVersion(version, "spec.version");
        String displayName = boundedRequiredString(map, "displayName", "spec", 256);
        String summary = boundedRequiredString(map, "summary", "spec", 4_000);
        String overview = boundedRequiredString(map, "overview", "spec", 20_000);
        SkillVisibility visibility = enumValue(
                requiredString(map, "visibility", "spec"), SkillVisibility.class, "spec.visibility");
        String changelog = boundedOptionalString(map, "changelog", "spec", 4_000);
        SkillSuiteBundleCoordinate entry = parseCoordinate(requiredString(map, "entry", "spec"), "spec.entry");
        List<SkillSuiteBundleMember> members = parseMembers(requiredList(map, "members", "spec"));
        if (members.stream().noneMatch(member -> member.coordinate().equals(entry))) {
            throw invalid("spec entry skill must be a member: " + entry.canonical());
        }
        validatePackageDirectories(members);

        return new SkillSuiteBundleManifest.Spec(
                mode, baseVersion, version, displayName, summary, overview, visibility,
                changelog, entry, members);
    }

    private List<SkillSuiteBundleMember> parseMembers(List<?> values) {
        if (values.isEmpty()) {
            throw invalid("spec.members must not be empty");
        }
        if (values.size() > MAX_MEMBERS) {
            throw invalid("spec.members exceeds max " + MAX_MEMBERS);
        }

        List<SkillSuiteBundleMember> members = new ArrayList<>(values.size());
        Set<SkillSuiteBundleCoordinate> coordinates = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String location = "spec.members[" + index + "]";
            if (!(values.get(index) instanceof Map<?, ?> memberMap)) {
                throw invalid(location + " must be an object");
            }
            rejectUnknownFields(memberMap, MEMBER_FIELDS, location);
            SkillSuiteBundleCoordinate skill = parseCoordinate(
                    requiredString(memberMap, "skill", location), location + ".skill");
            if (!coordinates.add(skill)) {
                throw invalid("duplicate member skill: " + skill.canonical());
            }

            boolean hasPackage = hasNonNull(memberMap, "package");
            boolean hasReference = hasNonNull(memberMap, "reference");
            if (hasPackage == hasReference) {
                throw invalid(location + " must define exactly one of package or reference");
            }

            SkillSuiteBundleMember.PackageSource packageSource = hasPackage
                    ? parsePackage(requiredMap(memberMap, "package", location), location + ".package")
                    : null;
            SkillSuiteBundleMember.ReferenceSource referenceSource = hasReference
                    ? parseReference(requiredMap(memberMap, "reference", location), location + ".reference")
                    : null;
            members.add(new SkillSuiteBundleMember(skill, packageSource, referenceSource));
        }
        return List.copyOf(members);
    }

    private SkillSuiteBundleMember.PackageSource parsePackage(Map<?, ?> map, String location) {
        rejectUnknownFields(map, PACKAGE_FIELDS, location);
        String rawPath = requiredString(map, "path", location);
        String path;
        try {
            path = SkillPackagePolicy.normalizeEntryPath(rawPath);
        } catch (IllegalArgumentException exception) {
            throw invalid("unsafe package path at " + location + ": " + safeMessage(exception));
        }
        SkillVisibility visibility = null;
        String rawVisibility = optionalString(map, "visibility", location);
        if (rawVisibility != null) {
            visibility = enumValue(rawVisibility, SkillVisibility.class, location + ".visibility");
        }
        return new SkillSuiteBundleMember.PackageSource(path, visibility);
    }

    private SkillSuiteBundleMember.ReferenceSource parseReference(Map<?, ?> map, String location) {
        rejectUnknownFields(map, REFERENCE_FIELDS, location);
        String version = requiredString(map, "version", location);
        validateVersion(version, location + ".version");
        return new SkillSuiteBundleMember.ReferenceSource(version);
    }

    private void validatePackageDirectories(List<SkillSuiteBundleMember> members) {
        List<String> directories = members.stream()
                .filter(member -> member.packageSource() != null)
                .map(member -> member.packageSource().path())
                .sorted()
                .toList();
        for (int left = 0; left < directories.size(); left++) {
            for (int right = left + 1; right < directories.size(); right++) {
                String first = directories.get(left);
                String second = directories.get(right);
                if (second.equals(first) || second.startsWith(first + "/")) {
                    throw invalid("package directories must not overlap: " + first + " and " + second);
                }
            }
        }
    }

    private SkillSuiteBundleCoordinate parseCoordinate(String raw, String location) {
        if (!raw.startsWith("@") || raw.indexOf('/') < 2 || raw.indexOf('/') != raw.lastIndexOf('/')) {
            throw invalid(location + " must be an @namespace/slug coordinate");
        }
        int slash = raw.indexOf('/');
        return coordinate(raw.substring(1, slash), raw.substring(slash + 1), location);
    }

    private SkillSuiteBundleCoordinate coordinate(String namespace, String slug, String location) {
        try {
            if (!"global".equals(namespace)) {
                SlugValidator.validate(namespace);
            }
            SlugValidator.validate(slug);
        } catch (DomainBadRequestException exception) {
            throw invalid(location + " contains an invalid coordinate");
        }
        return new SkillSuiteBundleCoordinate(namespace, slug);
    }

    private void validateVersion(String value, String location) {
        if (value != null && !PORTABLE_VERSION_PATTERN.matcher(value).matches()) {
            throw invalid(location + " must be an exact portable version");
        }
    }

    private void rejectUnknownFields(Map<?, ?> map, Set<String> allowed, String location) {
        Set<String> unknown = new LinkedHashSet<>();
        for (Object key : map.keySet()) {
            if (!(key instanceof String stringKey) || !allowed.contains(stringKey)) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw invalid("unknown field at " + location + ": " + String.join(", ", unknown));
        }
    }

    private Map<?, ?> requiredMap(Map<?, ?> map, String field, String location) {
        Object value = map.get(field);
        if (!(value instanceof Map<?, ?> nested)) {
            throw invalid(location + "." + field + " must be an object");
        }
        return nested;
    }

    private List<?> requiredList(Map<?, ?> map, String field, String location) {
        Object value = map.get(field);
        if (!(value instanceof List<?> list)) {
            throw invalid(location + "." + field + " must be a list");
        }
        return list;
    }

    private String requiredString(Map<?, ?> map, String field, String location) {
        String value = optionalString(map, field, location);
        if (value == null) {
            throw invalid(location + "." + field + " is required");
        }
        return value;
    }

    private String optionalString(Map<?, ?> map, String field, String location) {
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw invalid(location + "." + field + " must be a non-blank string");
        }
        return stringValue.trim();
    }

    private String boundedRequiredString(Map<?, ?> map, String field, String location, int maxLength) {
        String value = requiredString(map, field, location);
        if (value.length() > maxLength) {
            throw invalid(location + "." + field + " exceeds max length " + maxLength);
        }
        return value;
    }

    private String boundedOptionalString(Map<?, ?> map, String field, String location, int maxLength) {
        String value = optionalString(map, field, location);
        if (value != null && value.length() > maxLength) {
            throw invalid(location + "." + field + " exceeds max length " + maxLength);
        }
        return value;
    }

    private <E extends Enum<E>> E enumValue(String raw, Class<E> type, String location) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(location + " has unsupported value: " + raw);
        }
    }

    private boolean hasNonNull(Map<?, ?> map, String field) {
        return map.containsKey(field) && map.get(field) != null;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private DomainBadRequestException invalid(String detail) {
        return new DomainBadRequestException("error.suite.bundle.manifest.invalid", detail);
    }
}
