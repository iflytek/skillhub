package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.bundle.SkillBundle;
import com.iflytek.skillhub.domain.bundle.SkillBundleDraftService;
import com.iflytek.skillhub.domain.bundle.SkillBundleException;
import com.iflytek.skillhub.domain.bundle.SkillBundleItem;
import com.iflytek.skillhub.domain.bundle.SkillBundleItemRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleReviewService;
import com.iflytek.skillhub.domain.bundle.SkillBundleReviewTask;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersion;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionStatus;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.bundle.BuildSkillBundleDraftRequest;
import com.iflytek.skillhub.dto.bundle.SkillBundleDetailResponse;
import com.iflytek.skillhub.dto.bundle.SkillBundleVersionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Application façade for the skill bundle module. Maps controller payloads to
 * domain commands and translates aggregates to DTOs.
 */
@Service
public class SkillBundleAppService {

    private final SkillBundleDraftService draftService;
    private final SkillBundleReviewService reviewService;
    private final SkillBundleRepository bundleRepository;
    private final SkillBundleVersionRepository versionRepository;
    private final SkillBundleItemRepository itemRepository;
    private final NamespaceRepository namespaceRepository;
    private final Clock clock;

    public SkillBundleAppService(SkillBundleDraftService draftService,
                                 SkillBundleReviewService reviewService,
                                 SkillBundleRepository bundleRepository,
                                 SkillBundleVersionRepository versionRepository,
                                 SkillBundleItemRepository itemRepository,
                                 NamespaceRepository namespaceRepository,
                                 Clock clock) {
        this.draftService = draftService;
        this.reviewService = reviewService;
        this.bundleRepository = bundleRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.namespaceRepository = namespaceRepository;
        this.clock = clock;
    }

    @Transactional
    public SkillBundleVersionResponse buildDraft(String namespaceSlug,
                                                 BuildSkillBundleDraftRequest request,
                                                 String creator) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.namespace.notFound"));

        SkillBundleDraftService.BuildDraftCommand command = new SkillBundleDraftService.BuildDraftCommand(
                namespace.getId(), request.slug(), request.displayName(), request.summary(),
                request.version(), parseVersionSort(request.version()), request.type(),
                request.targetProjectTypes(), request.roleTags(),
                request.items().stream().map(item -> new SkillBundleDraftService.DraftItem(
                        item.skillId(), item.skillVersionId(), item.roleDescription(),
                        item.required(), item.installOrder())).toList(),
                "{}", "{}", placeholderStorageKey(namespaceSlug, request.slug(), request.version())
        );

        SkillBundleVersion version = draftService.buildDraft(command, creator);
        return SkillBundleVersionResponse.from(version);
    }

    @Transactional
    public SkillBundleReviewTask submitForReview(Long bundleVersionId, String submitter) {
        return reviewService.submitForReview(bundleVersionId, submitter);
    }

    @Transactional
    public SkillBundleReviewTask approve(Long reviewTaskId, String comment, String reviewer) {
        return reviewService.approve(reviewTaskId, comment, reviewer, clock.instant());
    }

    @Transactional
    public SkillBundleReviewTask reject(Long reviewTaskId, String comment, String reviewer) {
        return reviewService.reject(reviewTaskId, comment, reviewer);
    }

    @Transactional(readOnly = true)
    public SkillBundleDetailResponse getDetail(String namespaceSlug, String slug, String requestedVersion) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.namespace.notFound"));
        SkillBundle bundle = bundleRepository.findByNamespaceIdAndSlug(namespace.getId(), slug)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.notFound"));
        SkillBundleVersion version = resolveVisibleVersion(bundle, requestedVersion);

        List<SkillBundleItem> items = version == null
                ? List.of()
                : itemRepository.findByBundleVersionId(version.getId());
        List<SkillBundleDetailResponse.ItemView> views = items.stream()
                .map(this::toItemView)
                .sorted((a, b) -> Integer.compare(a.installOrder(), b.installOrder()))
                .toList();

        return SkillBundleDetailResponse.build(bundle, version, views);
    }

    @Transactional
    public void incrementDownload(String namespaceSlug, String slug) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.namespace.notFound"));
        SkillBundle bundle = bundleRepository.findByNamespaceIdAndSlug(namespace.getId(), slug)
                .orElseThrow(() -> new SkillBundleException("error.skillBundle.notFound"));
        bundleRepository.incrementDownloadCount(bundle.getId());
    }

    private SkillBundleVersion resolveVisibleVersion(SkillBundle bundle, String requestedVersion) {
        if (requestedVersion != null && !requestedVersion.isBlank()) {
            return versionRepository.findByBundleIdAndVersion(bundle.getId(), requestedVersion)
                    .filter(v -> v.getStatus() == SkillBundleVersionStatus.PUBLISHED)
                    .orElseThrow(() -> new SkillBundleException("error.skillBundle.version.notFound"));
        }
        if (bundle.getLatestVersionId() == null) {
            return null;
        }
        return versionRepository.findById(bundle.getLatestVersionId()).orElse(null);
    }

    private SkillBundleDetailResponse.ItemView toItemView(SkillBundleItem item) {
        return new SkillBundleDetailResponse.ItemView(
                item.getSkillId(), item.getNamespaceSlug(), item.getSkillSlug(),
                item.getDisplayName(), item.getVersion(),
                item.getRoleDescription(), item.isRequired(), item.getInstallOrder(),
                "/space/" + item.getNamespaceSlug() + "/" + item.getSkillSlug()
        );
    }

    private long parseVersionSort(String version) {
        // semver MAJOR.MINOR.PATCH packed into a sortable long; pre-release parts ignored.
        String[] parts = version.split("[.+-]");
        long sort = 0;
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                sort = sort * 1_000_000L + Long.parseLong(parts[i]);
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        return sort;
    }

    private String placeholderStorageKey(String namespaceSlug, String slug, String version) {
        return "bundles/" + namespaceSlug + "/" + slug + "/" + version + "/bundle.zip";
    }
}
