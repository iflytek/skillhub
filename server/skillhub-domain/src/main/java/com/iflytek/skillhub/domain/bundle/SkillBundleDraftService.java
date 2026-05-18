package com.iflytek.skillhub.domain.bundle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Application service that builds a skill bundle draft from a set of platform skills.
 * Captures the design-doc business rules:
 *
 * <ul>
 *   <li>Slug + version uniqueness inside a namespace.</li>
 *   <li>Each item must reference a {@code PUBLISHED} skill version (lock snapshot, no
 *       follow-latest).</li>
 *   <li>{@code PROJECT} bundles need at least one project type, {@code ROLE} bundles need
 *       at least one role tag.</li>
 *   <li>No duplicate skill coordinates within one bundle version.</li>
 * </ul>
 */
public class SkillBundleDraftService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillBundleRepository bundleRepository;
    private final SkillBundleVersionRepository versionRepository;
    private final SkillBundleItemRepository itemRepository;
    private final SkillBundleItemSourceResolver itemSourceResolver;

    public SkillBundleDraftService(SkillBundleRepository bundleRepository,
                                   SkillBundleVersionRepository versionRepository,
                                   SkillBundleItemRepository itemRepository,
                                   SkillBundleItemSourceResolver itemSourceResolver) {
        this.bundleRepository = bundleRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.itemSourceResolver = itemSourceResolver;
    }

    public SkillBundleVersion buildDraft(BuildDraftCommand command, String creator) {
        Objects.requireNonNull(command, "command");
        validateCommand(command);

        SkillBundle bundle = bundleRepository.findByNamespaceIdAndSlug(command.namespaceId(), command.slug())
                .orElseGet(() -> bundleRepository.save(new SkillBundle(
                        command.namespaceId(), command.slug(), command.displayName(),
                        command.summary(), command.bundleType(), creator, creator)));

        if (versionRepository.findByBundleIdAndVersion(bundle.getId(), command.version()).isPresent()) {
            throw new SkillBundleException("error.skillBundle.version.duplicate");
        }

        List<ResolvedDraftItem> resolvedItems = resolveItems(command.items());
        SkillBundleVersion version = versionRepository.save(new SkillBundleVersion(
                bundle.getId(), command.version(), command.versionSort(),
                buildManifestJson(command, resolvedItems), buildLockJson(resolvedItems), command.bundleStorageKey()));

        for (ResolvedDraftItem resolved : resolvedItems) {
            DraftItem item = resolved.item();
            SkillBundleItemSnapshot snapshot = resolved.snapshot();
            SkillBundleItem entity = new SkillBundleItem(
                    version.getId(), BundleItemSourceType.REGISTRY,
                    snapshot.namespaceSlug(), snapshot.skillSlug(), snapshot.version(),
                    snapshot.displayName(), item.roleDescription(),
                    item.required(), item.installOrder());
            entity.setSkillId(item.skillId());
            entity.setSkillVersionId(item.skillVersionId());
            entity.setSummary(snapshot.summary());
            itemRepository.save(entity);
        }
        return version;
    }

    private List<ResolvedDraftItem> resolveItems(List<DraftItem> items) {
        List<SkillBundleItemSnapshot> snapshots = itemSourceResolver.resolveRegistryItems(items);
        if (snapshots == null || snapshots.size() != items.size()) {
            throw new SkillBundleException("error.skillBundle.item.versionNotFound");
        }
        List<ResolvedDraftItem> resolved = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            resolved.add(new ResolvedDraftItem(items.get(i), snapshots.get(i)));
        }
        return resolved;
    }

    private String buildManifestJson(BuildDraftCommand command, List<ResolvedDraftItem> resolvedItems) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("slug", command.slug());
        root.put("displayName", command.displayName());
        root.put("version", command.version());
        root.put("type", command.bundleType().name());
        root.put("targetProjectTypes", safeList(command.targetProjectTypes()));
        root.put("roleTags", safeList(command.roleTags()));
        root.put("items", orderedItems(resolvedItems).stream().map(this::manifestItem).toList());
        return toJson(root);
    }

    private String buildLockJson(List<ResolvedDraftItem> resolvedItems) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("items", orderedItems(resolvedItems).stream().map(this::lockItem).toList());
        return toJson(root);
    }

    private List<ResolvedDraftItem> orderedItems(List<ResolvedDraftItem> resolvedItems) {
        return resolvedItems.stream()
                .sorted(Comparator.comparingInt((ResolvedDraftItem item) -> item.item().installOrder())
                        .thenComparing(item -> item.snapshot().namespaceSlug())
                        .thenComparing(item -> item.snapshot().skillSlug()))
                .toList();
    }

    private Map<String, Object> manifestItem(ResolvedDraftItem resolved) {
        DraftItem item = resolved.item();
        SkillBundleItemSnapshot snapshot = resolved.snapshot();
        Map<String, Object> node = lockItem(resolved);
        node.put("sourceType", BundleItemSourceType.REGISTRY.name());
        node.put("displayName", snapshot.displayName());
        node.put("summary", snapshot.summary());
        node.put("roleDescription", item.roleDescription());
        node.put("required", item.required());
        node.put("installOrder", item.installOrder());
        return node;
    }

    private Map<String, Object> lockItem(ResolvedDraftItem resolved) {
        DraftItem item = resolved.item();
        SkillBundleItemSnapshot snapshot = resolved.snapshot();
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("skillId", item.skillId());
        node.put("skillVersionId", item.skillVersionId());
        node.put("namespaceSlug", snapshot.namespaceSlug());
        node.put("skillSlug", snapshot.skillSlug());
        node.put("coordinate", "@" + snapshot.namespaceSlug() + "/" + snapshot.skillSlug());
        node.put("version", snapshot.version());
        node.put("publishedAt", snapshot.publishedAt() == null ? null : snapshot.publishedAt().toString());
        return node;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new SkillBundleException("error.skillBundle.manifest.invalid");
        }
    }

    private void validateCommand(BuildDraftCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new SkillBundleException("error.skillBundle.item.empty");
        }
        if (command.bundleType() == SkillBundleType.PROJECT
                && (command.targetProjectTypes() == null || command.targetProjectTypes().isEmpty())) {
            throw new SkillBundleException("error.skillBundle.projectTypes.required");
        }
        if (command.bundleType() == SkillBundleType.ROLE
                && (command.roleTags() == null || command.roleTags().isEmpty())) {
            throw new SkillBundleException("error.skillBundle.roleTags.required");
        }
        long unique = command.items().stream()
                .map(i -> i.skillId() + "@" + i.skillVersionId())
                .distinct()
                .count();
        if (unique != command.items().size()) {
            throw new SkillBundleException("error.skillBundle.item.duplicate");
        }
    }

    public record BuildDraftCommand(Long namespaceId,
                                    String slug,
                                    String displayName,
                                    String summary,
                                    String version,
                                    long versionSort,
                                    SkillBundleType bundleType,
                                    List<String> targetProjectTypes,
                                    List<String> roleTags,
                                    List<DraftItem> items,
                                    String bundleStorageKey) {}

    public record DraftItem(Long skillId,
                            Long skillVersionId,
                            String roleDescription,
                            boolean required,
                            int installOrder) {}

    public record SkillBundleItemSnapshot(String namespaceSlug,
                                          String skillSlug,
                                          String displayName,
                                          String version,
                                          String summary,
                                          Instant publishedAt) {}

    private record ResolvedDraftItem(DraftItem item, SkillBundleItemSnapshot snapshot) {}

    /**
     * Resolves a published skill version snapshot for a bundle item.
     * Implementations live in {@code skillhub-app} so the domain layer doesn't depend on JPA.
     */
    public interface SkillBundleItemSourceResolver {
        SkillBundleItemSnapshot resolveRegistryItem(Long skillId, Long skillVersionId);

        default List<SkillBundleItemSnapshot> resolveRegistryItems(List<DraftItem> items) {
            return items.stream()
                    .map(item -> resolveRegistryItem(item.skillId(), item.skillVersionId()))
                    .toList();
        }
    }
}
