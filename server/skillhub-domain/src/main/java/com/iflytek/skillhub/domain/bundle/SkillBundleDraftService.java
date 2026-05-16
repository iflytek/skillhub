package com.iflytek.skillhub.domain.bundle;

import java.time.Instant;
import java.util.List;
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

        SkillBundleVersion version = versionRepository.save(new SkillBundleVersion(
                bundle.getId(), command.version(), command.versionSort(),
                command.manifestJson(), command.lockJson(), command.bundleStorageKey()));

        for (DraftItem item : command.items()) {
            SkillBundleItemSnapshot snapshot = itemSourceResolver.resolveRegistryItem(item.skillId(), item.skillVersionId());
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
                                    String manifestJson,
                                    String lockJson,
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

    /**
     * Resolves a published skill version snapshot for a bundle item.
     * Implementations live in {@code skillhub-app} so the domain layer doesn't depend on JPA.
     */
    public interface SkillBundleItemSourceResolver {
        SkillBundleItemSnapshot resolveRegistryItem(Long skillId, Long skillVersionId);
    }
}
